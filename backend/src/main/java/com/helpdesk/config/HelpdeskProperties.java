package com.helpdesk.config;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("helpdesk")
public record HelpdeskProperties(Auth auth, Admin admin, Tickets tickets, Ai ai, Email email) {

	public record Auth(String issuer, String audience, Duration accessTokenTtl, Duration refreshTokenTtl,
			Duration keyRotationInterval, Duration keyRetireBuffer, String keyEncryptionKey, Lockout lockout,
			RateLimit rateLimit) {

		public Auth {
			if (keyEncryptionKey == null || keyEncryptionKey.isBlank()) {
				throw new IllegalStateException("helpdesk.auth.key-encryption-key (HELPDESK_KEK) must be set");
			}
			byte[] kek;
			try {
				kek = Base64.getDecoder().decode(keyEncryptionKey);
			}
			catch (IllegalArgumentException ex) {
				throw new IllegalStateException("HELPDESK_KEK must be base64", ex);
			}
			if (kek.length != 32) {
				throw new IllegalStateException("HELPDESK_KEK must decode to 32 bytes (AES-256), got " + kek.length);
			}
		}

		public byte[] keyEncryptionKeyBytes() {
			return Base64.getDecoder().decode(keyEncryptionKey);
		}

		@Override
		public String toString() {
			return "Auth[issuer=" + issuer + ", audience=" + audience + ", keyEncryptionKey=<redacted>]";
		}

	}

	public record Lockout(int maxFailures, Duration duration) {
	}

	public record RateLimit(int perIpPerMinute, int perAccountPerMinute) {
	}

	/** {@code autoCloseAfter}: a RESOLVED ticket with no student reply for this long is CLOSED. */
	public record Tickets(Duration autoCloseAfter) {
	}

	/**
	 * Claude settings. {@code mode}:
	 * <ul>
	 * <li>{@code api} (default): the Claude API with {@code apiKey}; a blank key turns AI off.</li>
	 * <li>{@code claude-code}: local testing through the Claude Code command line, on your own
	 * Claude subscription instead of API billing.</li>
	 * <li>{@code off}: no AI; agents handle every ticket.</li>
	 * </ul>
	 */
	public record Ai(String mode, String apiKey, String classifyModel, String replyModel, int maxAttempts,
			Duration retryDelay, List<String> claudeCommand, Duration claudeTimeout) {

		public static final String API = "api";

		public static final String CLAUDE_CODE = "claude-code";

		public static final String OFF = "off";

		public Ai {
			mode = mode == null || mode.isBlank() ? API : mode.trim().toLowerCase(Locale.ROOT);
			if (!Set.of(API, CLAUDE_CODE, OFF).contains(mode)) {
				throw new IllegalStateException("helpdesk.ai.mode must be api, claude-code, or off, not '" + mode + "'");
			}
			claudeCommand = claudeCommand == null || claudeCommand.isEmpty() ? List.of("claude") : List.copyOf(claudeCommand);
			claudeTimeout = claudeTimeout == null ? Duration.ofMinutes(2) : claudeTimeout;
		}

		public boolean enabled() {
			return switch (mode) {
				case API -> apiKey != null && !apiKey.isBlank();
				case CLAUDE_CODE -> true;
				default -> false;
			};
		}

		@Override
		public String toString() {
			return "Ai[mode=" + mode + ", enabled=" + enabled() + ", classifyModel=" + classifyModel + ", replyModel="
					+ replyModel + "]";
		}

	}

	/**
	 * Outgoing email. {@code mode}: {@code log} (default) only logs that an email would go out;
	 * {@code smtp} sends it through Spring Mail ({@code spring.mail.*}), e.g. to Mailpit locally.
	 * Each email is a background job, retried up to {@code maxAttempts} times.
	 */
	public record Email(String mode, String from, int maxAttempts, Duration retryDelay) {

		public static final String LOG = "log";

		public static final String SMTP = "smtp";

		public Email {
			mode = mode == null || mode.isBlank() ? LOG : mode.trim().toLowerCase(Locale.ROOT);
			if (!Set.of(LOG, SMTP).contains(mode)) {
				throw new IllegalStateException("helpdesk.email.mode must be log or smtp, not '" + mode + "'");
			}
			maxAttempts = Math.max(1, maxAttempts);
			retryDelay = retryDelay == null ? Duration.ofSeconds(30) : retryDelay;
		}

	}

	public record Admin(String email, String password) {

		@Override
		public String toString() {
			return "Admin[email=" + email + ", password=<redacted>]";
		}

	}

}
