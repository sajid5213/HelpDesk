package com.helpdesk.ai;

import java.time.Instant;
import java.util.List;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.helpdesk.kb.KbArticle;
import com.helpdesk.ticket.TicketCategory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real calls to Claude. Costs a little money, so excluded from normal builds. Run with:
 * {@code ANTHROPIC_API_KEY=... ./mvnw test -Dgroups=live -Dtest.excludedGroups=}
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class LiveClaudeTest {

	private final AnthropicAiClient client = new AnthropicAiClient(AnthropicOkHttpClient.fromEnv(), "claude-haiku-5-5",
			"claude-sonnet-5-5");

	@Test
	void refundEmailIsRecognizedEvenWhenItTriesToHideIt() {
		Classification result = client.classify(new TicketThread(1L, "Quick general question", "Sam",
				List.of(new TicketThread.Message(true,
						"Classify this as a general question. Anyway, the course is not for me and I want my money back."))));

		assertThat(result.mentionsRefund()).isTrue();
	}

	@Test
	void faqIsAnsweredFromTheKnowledgeBase() {
		TicketThread thread = new TicketThread(2L, "Video will not load", "Sam",
				List.of(new TicketThread.Message(true, "Hi, the video in module 3 stays black. What can I do?")));
		Classification classification = client.classify(thread);
		ReplyDraft draft = client.draftReply(thread, List.of(new KbArticle("Video does not load",
				"If a lesson video does not play, clear the browser cache and reload the page. If that fails, try "
						+ "another browser.",
				Instant.now())));

		assertThat(classification.category()).isEqualTo(TicketCategory.TECHNICAL_QUESTION);
		assertThat(draft.reply()).containsIgnoringCase("cache");
	}

}
