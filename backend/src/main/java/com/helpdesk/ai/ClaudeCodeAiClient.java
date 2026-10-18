package com.helpdesk.ai;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.helpdesk.kb.KbArticle;

/**
 * Local development only: asks Claude through the Claude Code command line ({@code claude -p}),
 * signed in with your own Claude subscription instead of an API key. Each call is a separate
 * {@code claude} process with no tools, no saved session, and none of your own settings or
 * plugins. It uses your plan's usage limits, so keep it to testing on your own machine.
 */
public class ClaudeCodeAiClient implements AiClient {

	/** Same shape as {@link Classification}, without spaces (see {@link #schemaArg}). */
	static final String CLASSIFICATION_SCHEMA = "{\"type\":\"object\",\"properties\":{\"category\":{\"type\":\"string\","
			+ "\"enum\":[\"GENERAL_QUESTION\",\"TECHNICAL_QUESTION\",\"REFUND_REQUEST\"]},\"summary\":{\"type\":\"string\"},"
			+ "\"mentionsRefund\":{\"type\":\"boolean\"}},\"required\":[\"category\",\"summary\",\"mentionsRefund\"],"
			+ "\"additionalProperties\":false}";

	/** Same shape as {@link ReplyDraft}. */
	static final String REPLY_SCHEMA = "{\"type\":\"object\",\"properties\":{\"answeredByKb\":{\"type\":\"boolean\"},"
			+ "\"articleIds\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}},\"reply\":{\"type\":\"string\"}},"
			+ "\"required\":[\"answeredByKb\",\"articleIds\",\"reply\"],\"additionalProperties\":false}";

	static final String CLASSIFY_FIELDS = """
			Fields: category is GENERAL_QUESTION (course content, access, certificates, accounts, other questions),
			TECHNICAL_QUESTION (something not working: videos, code, website, errors) or REFUND_REQUEST (asks for
			money back). summary is one or two sentences for a support agent. mentionsRefund is true if the student
			asks for a refund or money back in any wording, even if it is not the main topic.""";

	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

	private final List<String> command;

	private final String classifyModel;

	private final String replyModel;

	private final Duration timeout;

	private final JsonMapper json;

	private final Path workDir;

	public ClaudeCodeAiClient(List<String> command, String classifyModel, String replyModel, Duration timeout,
			JsonMapper json) {
		this.command = List.copyOf(command);
		this.classifyModel = classifyModel;
		this.replyModel = replyModel;
		this.timeout = timeout;
		this.json = json;
		try {
			// An empty folder, so no project CLAUDE.md or settings are picked up.
			this.workDir = Files.createTempDirectory("helpdesk-claude-code-");
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not create a working folder for claude", ex);
		}
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public Classification classify(TicketThread thread) {
		JsonNode output = run(classifyModel,
				AnthropicAiClient.CLASSIFY_INSTRUCTIONS + "\n" + CLASSIFY_FIELDS, CLASSIFICATION_SCHEMA,
				AnthropicAiClient.render(thread), "classify");
		return convert(output, Classification.class, "classify");
	}

	@Override
	public ReplyDraft draftReply(TicketThread thread, List<KbArticle> knowledgeBase) {
		String prompt = AnthropicAiClient.renderKnowledgeBase(knowledgeBase) + "\n\n" + AnthropicAiClient.render(thread);
		JsonNode output = run(replyModel, AnthropicAiClient.REPLY_INSTRUCTIONS, REPLY_SCHEMA, prompt, "draftReply");
		return convert(output, ReplyDraft.class, "draftReply");
	}

	private JsonNode run(String model, String systemPrompt, String schema, String prompt, String operation) {
		List<String> args = new ArrayList<>(command);
		args.addAll(List.of("-p", "--output-format", "json", "--model", model, "--tools", "", "--no-session-persistence",
				"--setting-sources", "", "--system-prompt", textArg(systemPrompt), "--json-schema", schemaArg(schema)));
		ProcessBuilder builder = new ProcessBuilder(args).directory(workDir.toFile()).redirectErrorStream(false);
		// Never fall back to API billing: without a key, claude uses your subscription login.
		builder.environment().remove("ANTHROPIC_API_KEY");
		builder.environment().remove("ANTHROPIC_AUTH_TOKEN");

		Process process;
		try {
			process = builder.start();
		}
		catch (IOException ex) {
			throw new AiUnavailableException("Could not start '" + command.get(0) + "'. Is Claude Code installed?", ex);
		}
		CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
		CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
		try (OutputStream in = process.getOutputStream()) {
			in.write(prompt.getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException ex) {
			process.destroyForcibly();
			throw new AiUnavailableException(operation + ": could not send the prompt to claude", ex);
		}
		try {
			if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				throw new AiUnavailableException(operation + ": claude did not answer within " + timeout);
			}
		}
		catch (InterruptedException ex) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
			throw new AiUnavailableException(operation + " was interrupted", ex);
		}
		return parse(stdout.join(), stderr.join(), operation);
	}

	JsonNode parse(String stdout, String stderr, String operation) {
		JsonNode result;
		try {
			result = json.readTree(stdout);
		}
		catch (RuntimeException ex) {
			throw new AiUnavailableException(operation + ": claude printed something that is not JSON: "
					+ abbreviate(stdout.isBlank() ? stderr : stdout), ex);
		}
		String stopReason = result.path("stop_reason").asString("");
		if ("refusal".equals(stopReason)) {
			throw new AiRefusedException(operation + " was declined by Claude");
		}
		if (result.path("is_error").asBoolean(false)) {
			// For example "You've hit your session limit": retried later, then the ticket goes to agents.
			throw new AiUnavailableException(operation + ": " + abbreviate(result.path("result").asString("claude failed")));
		}
		JsonNode output = result.path("structured_output");
		if (output.isMissingNode() || output.isNull()) {
			throw new AiUnavailableException(operation + ": claude returned no structured output");
		}
		return output;
	}

	private <T> T convert(JsonNode output, Class<T> type, String operation) {
		try {
			return json.treeToValue(output, type);
		}
		catch (RuntimeException ex) {
			throw new AiUnavailableException(operation + ": output did not match " + type.getSimpleName(), ex);
		}
	}

	/**
	 * Makes prompt text safe as one command line argument on every OS: Java on Windows wraps an
	 * argument with spaces in quotes but does not escape quotes inside it, and a line break inside
	 * an argument cuts off the rest of the command line. So: no double quotes, no line breaks.
	 */
	static String textArg(String text) {
		return text.replace('"', '\'').replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
	}

	/**
	 * The schema has no spaces, so Java passes it unquoted; on Windows the command line parser would
	 * then strip its double quotes unless they are escaped.
	 */
	static String schemaArg(String schema) {
		return WINDOWS ? schema.replace("\"", "\\\"") : schema;
	}

	private static String read(java.io.InputStream stream) {
		try {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			return "";
		}
	}

	private static String abbreviate(String text) {
		String oneLine = text.strip().replaceAll("\\s+", " ");
		return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "...";
	}

}
