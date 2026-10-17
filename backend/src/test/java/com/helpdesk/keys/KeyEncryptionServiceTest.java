package com.helpdesk.keys;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class KeyEncryptionServiceTest {

	private static final byte[] KEK = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

	private static final byte[] OTHER_KEK = "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.US_ASCII);

	private final KeyEncryptionService encryption = new KeyEncryptionService(KEK);

	@Test
	void encryptDecryptRoundTrip() {
		byte[] secret = "private key bytes".getBytes(StandardCharsets.UTF_8);

		assertThat(encryption.decrypt(encryption.encrypt(secret))).isEqualTo(secret);
	}

	@Test
	void sameInputEncryptsDifferentlyEachTime() {
		byte[] secret = "private key bytes".getBytes(StandardCharsets.UTF_8);

		assertThat(encryption.encrypt(secret)).isNotEqualTo(encryption.encrypt(secret));
	}

	@Test
	void decryptWithWrongKekFails() {
		String encrypted = encryption.encrypt("private key bytes".getBytes(StandardCharsets.UTF_8));

		assertThatIllegalStateException().isThrownBy(() -> new KeyEncryptionService(OTHER_KEK).decrypt(encrypted));
	}

	@Test
	void tamperedCiphertextFails() {
		String encrypted = encryption.encrypt("private key bytes".getBytes(StandardCharsets.UTF_8));
		char last = encrypted.charAt(encrypted.length() - 3);
		String tampered = encrypted.substring(0, encrypted.length() - 3) + (last == 'A' ? 'B' : 'A')
				+ encrypted.substring(encrypted.length() - 2);

		assertThatIllegalStateException().isThrownBy(() -> encryption.decrypt(tampered));
	}

}
