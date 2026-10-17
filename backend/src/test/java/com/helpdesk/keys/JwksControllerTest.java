package com.helpdesk.keys;

import java.time.Duration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.KeyUse;
import org.junit.jupiter.api.Test;

import com.helpdesk.common.IntegrationTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;

import static org.assertj.core.api.Assertions.assertThat;

class JwksControllerTest extends IntegrationTest {

	@Autowired
	SigningKeyService signingKeys;

	@Test
	void jwksIsPublicAndListsActiveKey() throws Exception {
		String activeKid = signingKeys.activeKey().getKeyID();

		String body = client.get()
			.uri("/.well-known/jwks.json")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
			.returnResult(String.class)
			.getResponseBody();

		JWK key = JWKSet.parse(body).getKeyByKeyId(activeKid);
		assertThat(key).isNotNull();
		assertThat(key.getKeyType()).isEqualTo(KeyType.RSA);
		assertThat(key.getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
		assertThat(key.getKeyUse()).isEqualTo(KeyUse.SIGNATURE);
	}

	@Test
	void jwksNeverContainsPrivateParts() throws Exception {
		String body = client.get().uri("/.well-known/jwks.json").exchange().returnResult(String.class).getResponseBody();

		assertThat(JWKSet.parse(body).getKeys()).isNotEmpty().allSatisfy((key) -> assertThat(key.isPrivate()).isFalse());
		assertThat(body).doesNotContain("\"d\"", "\"p\"", "\"q\"", "\"dp\"", "\"dq\"", "\"qi\"");
	}

}
