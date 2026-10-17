package com.helpdesk.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/** Builds and signs arbitrary tokens, so tests can forge the bad ones the app must reject. */
final class TestTokens {

	static final String ISSUER = "https://helpdesk.local";

	static final String AUDIENCE = "helpdesk-api";

	private TestTokens() {
	}

	static JWTClaimsSet.Builder validClaims(String subject, String role, Instant now) {
		return new JWTClaimsSet.Builder().subject(subject)
			.claim("roles", List.of(role))
			.jwtID(UUID.randomUUID().toString())
			.issueTime(Date.from(now))
			.expirationTime(Date.from(now.plus(Duration.ofMinutes(15))))
			.issuer(ISSUER)
			.audience(List.of(AUDIENCE));
	}

	static String sign(RSAKey key, JWTClaimsSet claims) {
		try {
			SignedJWT jwt = new SignedJWT(
					new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(),
					claims);
			jwt.sign(new RSASSASigner(key));
			return jwt.serialize();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	static RSAKey unknownKey() {
		try {
			return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
