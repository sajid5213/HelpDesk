package com.helpdesk.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import com.helpdesk.config.HelpdeskProperties;
import com.helpdesk.keys.SigningKeyService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Validates access tokens against our own published keys, read straight from the database
 * (no HTTP call to our own JWKS endpoint). A key removed from JWKS can no longer verify anything.
 */
@Configuration(proxyBeanMethods = false)
class JwtDecoderConfig {

	/** Small allowance for clock drift between servers. */
	static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

	@Bean
	JwtDecoder jwtDecoder(SigningKeyService signingKeys, HelpdeskProperties properties, Clock clock) {
		JWKSource<SecurityContext> publishedKeys = (selector, context) -> selector
			.select(new JWKSet(List.<JWK>copyOf(signingKeys.publishedKeys())));
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(publishedKeys)
			.jwsAlgorithm(SignatureAlgorithm.RS256)
			.build();
		decoder.setJwtValidator(validator(properties.auth().issuer(), properties.auth().audience(), clock));
		return decoder;
	}

	static OAuth2TokenValidator<Jwt> validator(String issuer, String audience, Clock clock) {
		JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
		timestamps.setClock(clock);
		return new DelegatingOAuth2TokenValidator<>(requireExpiry(), timestamps, new JwtIssuerValidator(issuer),
				new JwtClaimValidator<List<String>>("aud", (aud) -> aud != null && aud.contains(audience)));
	}

	/** The timestamp validator skips tokens without exp; ours must always expire. */
	private static OAuth2TokenValidator<Jwt> requireExpiry() {
		return (jwt) -> jwt.getExpiresAt() != null ? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token has no expiry", null));
	}

}
