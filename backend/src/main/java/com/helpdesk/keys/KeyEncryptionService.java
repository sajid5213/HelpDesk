package com.helpdesk.keys;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.helpdesk.config.HelpdeskProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Encrypts signing-key material at rest with AES-256-GCM. Output is base64(iv || ciphertext+tag),
 * with a fresh random 12-byte IV for every call.
 */
@Service
public class KeyEncryptionService {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private static final int IV_BYTES = 12;

	private static final int TAG_BITS = 128;

	private final SecretKey kek;

	private final SecureRandom random = new SecureRandom();

	@Autowired
	KeyEncryptionService(HelpdeskProperties properties) {
		this(properties.auth().keyEncryptionKeyBytes());
	}

	/** For unit tests. */
	KeyEncryptionService(byte[] kek) {
		this.kek = new SecretKeySpec(kek, "AES");
	}

	public String encrypt(byte[] plaintext) {
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, kek, new GCMParameterSpec(TAG_BITS, iv));
			byte[] ciphertext = cipher.doFinal(plaintext);
			return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
				.put(iv)
				.put(ciphertext)
				.array());
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Could not encrypt key material", ex);
		}
	}

	public byte[] decrypt(String encoded) {
		try {
			byte[] data = Base64.getDecoder().decode(encoded);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, kek, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
			return cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
		}
		catch (GeneralSecurityException ex) {
			// Wrong KEK or tampered data: GCM authentication fails.
			throw new IllegalStateException("Could not decrypt key material", ex);
		}
	}

}
