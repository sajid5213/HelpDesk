package com.helpdesk.keys;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helpdesk.config.HelpdeskProperties;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SigningKeyService {

	private static final Logger log = LoggerFactory.getLogger(SigningKeyService.class);

	private static final int KEY_SIZE = 2048;

	private final SigningKeyRepository keys;

	private final KeyEncryptionService encryption;

	private final Clock clock;

	private final Duration publishWindowAfterRetire;

	/** Decrypted private keys by kid, so signing does not decrypt on every token. */
	private final Map<String, RSAKey> privateKeyCache = new ConcurrentHashMap<>();

	SigningKeyService(SigningKeyRepository keys, KeyEncryptionService encryption, Clock clock,
			HelpdeskProperties properties) {
		this.keys = keys;
		this.encryption = encryption;
		this.clock = clock;
		this.publishWindowAfterRetire = properties.auth().accessTokenTtl().plus(properties.auth().keyRetireBuffer());
	}

	/** Creates the first ACTIVE key if none exists. Safe to call at every startup. */
	@Transactional
	public SigningKey ensureActiveKey() {
		return keys.findByStatus(KeyStatus.ACTIVE).orElseGet(() -> {
			try {
				return keys.saveAndFlush(newKey());
			}
			catch (DataIntegrityViolationException ex) {
				// Another instance created it at the same moment; the unique index kept us to one.
				return keys.findByStatus(KeyStatus.ACTIVE).orElseThrow(() -> ex);
			}
		});
	}

	/** The key that signs new tokens, including its private part. */
	@Transactional(readOnly = true)
	public RSAKey activeKey() {
		SigningKey active = keys.findByStatus(KeyStatus.ACTIVE)
			.orElseThrow(() -> new IllegalStateException("No ACTIVE signing key"));
		return privateKeyCache.computeIfAbsent(active.getKid(), (kid) -> withPrivateKey(active));
	}

	/** Makes a new ACTIVE key and retires the previous one, in one transaction. */
	@Transactional
	public SigningKey rotate() {
		keys.lockActive().ifPresent((current) -> {
			current.retire(clock.instant());
			keys.saveAndFlush(current);
		});
		SigningKey created = keys.saveAndFlush(newKey());
		log.info("Rotated signing key, new kid {}", created.getKid());
		return created;
	}

	/**
	 * Public keys a token may be verified with: the ACTIVE key, plus RETIRED keys whose
	 * tokens could still be unexpired (retired less than access-token TTL + buffer ago).
	 */
	@Transactional(readOnly = true)
	public List<RSAKey> publishedKeys() {
		Instant cutoff = clock.instant().minus(publishWindowAfterRetire);
		List<RSAKey> published = new ArrayList<>();
		for (SigningKey key : keys.findAllByOrderByCreatedAtDesc()) {
			boolean stillPublished = key.getStatus() == KeyStatus.ACTIVE || key.getRetiredAt().isAfter(cutoff);
			if (stillPublished) {
				published.add(publicKey(key));
			}
		}
		return published;
	}

	private SigningKey newKey() {
		try {
			RSAKey generated = new RSAKeyGenerator(KEY_SIZE).keyID(UUID.randomUUID().toString())
				.keyUse(KeyUse.SIGNATURE)
				.algorithm(JWSAlgorithm.RS256)
				.generate();
			String encryptedPrivate = encryption.encrypt(generated.toRSAPrivateKey().getEncoded());
			return new SigningKey(generated.getKeyID(), generated.toPublicJWK().toJSONString(), encryptedPrivate,
					clock.instant());
		}
		catch (JOSEException ex) {
			throw new IllegalStateException("Could not generate signing key", ex);
		}
	}

	private static RSAKey publicKey(SigningKey key) {
		try {
			return RSAKey.parse(key.getPublicJwk());
		}
		catch (ParseException ex) {
			throw new IllegalStateException("Stored public key " + key.getKid() + " is unreadable", ex);
		}
	}

	private RSAKey withPrivateKey(SigningKey key) {
		try {
			byte[] pkcs8 = encryption.decrypt(key.getPrivateKeyEncrypted());
			RSAPrivateKey privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA")
				.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
			return new RSAKey.Builder(publicKey(key)).privateKey(privateKey).build();
		}
		catch (java.security.GeneralSecurityException ex) {
			throw new IllegalStateException("Stored private key " + key.getKid() + " is unreadable", ex);
		}
	}

}
