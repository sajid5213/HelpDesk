package com.helpdesk.config;

import java.time.Duration;
import java.util.Base64;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("helpdesk")
public record HelpdeskProperties(Auth auth, Admin admin, Tickets tickets) {

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

	public record Admin(String email, String password) {

		@Override
		public String toString() {
			return "Admin[email=" + email + ", password=<redacted>]";
		}

	}

}
