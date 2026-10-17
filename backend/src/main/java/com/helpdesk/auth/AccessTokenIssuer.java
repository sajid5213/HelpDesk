package com.helpdesk.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import com.helpdesk.config.HelpdeskProperties;
import com.helpdesk.keys.SigningKeyService;
import com.helpdesk.user.User;

import org.springframework.stereotype.Service;

/** Signs RS256 access tokens with the ACTIVE key. Hand-written on purpose: no authorization server library. */
@Service
public class AccessTokenIssuer {

	private final SigningKeyService signingKeys;

	private final Clock clock;

	private final String issuer;

	private final String audience;

	private final Duration ttl;

	AccessTokenIssuer(SigningKeyService signingKeys, Clock clock, HelpdeskProperties properties) {
		this.signingKeys = signingKeys;
		this.clock = clock;
		this.issuer = properties.auth().issuer();
		this.audience = properties.auth().audience();
		this.ttl = properties.auth().accessTokenTtl();
	}

	public IssuedAccessToken issue(User user) {
		RSAKey key = signingKeys.activeKey();
		Instant now = clock.instant();
		Instant expiresAt = now.plus(ttl);
		JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID())
			.type(JOSEObjectType.JWT)
			.build();
		JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(user.getId().toString())
			.claim("roles", List.of(user.getRole().name()))
			.jwtID(UUID.randomUUID().toString())
			.issueTime(Date.from(now))
			.expirationTime(Date.from(expiresAt))
			.issuer(issuer)
			.audience(List.of(audience))
			.build();
		SignedJWT jwt = new SignedJWT(header, claims);
		try {
			jwt.sign(new RSASSASigner(key));
		}
		catch (JOSEException ex) {
			throw new IllegalStateException("Could not sign access token", ex);
		}
		return new IssuedAccessToken(jwt.serialize(), expiresAt);
	}

	public record IssuedAccessToken(String token, Instant expiresAt) {
	}

}
