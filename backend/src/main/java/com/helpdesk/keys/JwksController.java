package com.helpdesk.keys;

import java.time.Duration;
import java.util.Map;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Publishes the public signing keys so any service can verify our access tokens. */
@RestController
class JwksController {

	private final SigningKeyService signingKeys;

	JwksController(SigningKeyService signingKeys) {
		this.signingKeys = signingKeys;
	}

	@GetMapping("/.well-known/jwks.json")
	ResponseEntity<Map<String, Object>> jwks() {
		// toPublicJWK() again as a second guard: private parts must never leave this method.
		JWKSet set = new JWKSet(signingKeys.publishedKeys().stream().map(JWK::toPublicJWK).toList());
		return ResponseEntity.ok()
			.cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
			.body(set.toJSONObject(true));
	}

}
