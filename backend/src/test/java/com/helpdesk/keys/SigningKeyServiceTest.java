package com.helpdesk.keys;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;

import com.helpdesk.common.IntegrationTest;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class SigningKeyServiceTest extends IntegrationTest {

	@Autowired
	SigningKeyService signingKeys;

	@Autowired
	SigningKeyRepository repository;

	@Test
	void startupLeavesExactlyOneActiveKey() {
		assertThat(repository.findAll()).filteredOn((k) -> k.getStatus() == KeyStatus.ACTIVE).hasSize(1);
	}

	@Test
	void ensureActiveKeyIsIdempotent() {
		String kid = signingKeys.ensureActiveKey().getKid();

		assertThat(signingKeys.ensureActiveKey().getKid()).isEqualTo(kid);
	}

	@Test
	void privateKeyIsNotStoredInPlaintext() throws Exception {
		RSAKey active = signingKeys.activeKey();
		SigningKey stored = repository.findById(active.getKeyID()).orElseThrow();
		String plainPkcs8 = Base64.getEncoder().encodeToString(active.toRSAPrivateKey().getEncoded());

		assertThat(stored.getPrivateKeyEncrypted()).isNotEqualTo(plainPkcs8).doesNotContain(plainPkcs8.substring(0, 40));
		assertThat(stored.getPublicJwk()).doesNotContain("\"d\"");
		assertThat(active.isPrivate()).isTrue();
	}

	@Test
	void rotateLeavesOneActiveAndRetiresPrevious() {
		String before = signingKeys.activeKey().getKeyID();

		SigningKey created = signingKeys.rotate();

		assertThat(created.getKid()).isNotEqualTo(before);
		assertThat(signingKeys.activeKey().getKeyID()).isEqualTo(created.getKid());
		SigningKey previous = repository.findById(before).orElseThrow();
		assertThat(previous.getStatus()).isEqualTo(KeyStatus.RETIRED);
		assertThat(previous.getRetiredAt()).isNotNull();
		assertThat(repository.findAll()).filteredOn((k) -> k.getStatus() == KeyStatus.ACTIVE).hasSize(1);
	}

	@Test
	void retiredKeyPublishedUntilTtlPlusBufferThenDropped() {
		String retiredKid = signingKeys.activeKey().getKeyID();
		signingKeys.rotate();

		// 15 min access-token TTL + 5 min buffer = 20 min window.
		clock.advance(Duration.ofMinutes(19));
		assertThat(kids(signingKeys.publishedKeys())).contains(retiredKid);

		clock.advance(Duration.ofMinutes(2));
		assertThat(kids(signingKeys.publishedKeys())).doesNotContain(retiredKid)
			.contains(signingKeys.activeKey().getKeyID());
	}

	private static List<String> kids(List<RSAKey> keys) {
		return keys.stream().map(RSAKey::getKeyID).toList();
	}

}
