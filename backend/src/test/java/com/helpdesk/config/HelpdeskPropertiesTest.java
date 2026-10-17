package com.helpdesk.config;

import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.helpdesk.config.HelpdeskProperties.Auth;
import com.helpdesk.config.HelpdeskProperties.Lockout;
import com.helpdesk.config.HelpdeskProperties.RateLimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class HelpdeskPropertiesTest {

	private static Auth auth(String kek) {
		return new Auth("iss", "aud", Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofDays(7),
				Duration.ofMinutes(5), kek, new Lockout(5, Duration.ofMinutes(15)), new RateLimit(20, 5));
	}

	@Test
	void acceptsBase64Key32Bytes() {
		String kek = Base64.getEncoder().encodeToString(new byte[32]);
		assertThat(auth(kek).keyEncryptionKeyBytes()).hasSize(32);
	}

	@Test
	void rejectsMissingKek() {
		assertThatIllegalStateException().isThrownBy(() -> auth("")).withMessageContaining("HELPDESK_KEK");
		assertThatIllegalStateException().isThrownBy(() -> auth(null));
	}

	@Test
	void rejectsKekOfWrongLength() {
		String shortKek = Base64.getEncoder().encodeToString(new byte[16]);
		assertThatIllegalStateException().isThrownBy(() -> auth(shortKek)).withMessageContaining("32 bytes");
	}

	@Test
	void rejectsNonBase64Kek() {
		assertThatIllegalStateException().isThrownBy(() -> auth("not base64 !!"));
	}

	@Test
	void toStringNeverPrintsTheKey() {
		String kek = Base64.getEncoder().encodeToString(new byte[32]);
		assertThat(auth(kek).toString()).doesNotContain(kek);
	}

}
