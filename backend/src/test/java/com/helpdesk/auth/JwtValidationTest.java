package com.helpdesk.auth;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import com.helpdesk.common.IntegrationTest;
import com.helpdesk.keys.SigningKeyService;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

@Import(JwtValidationTest.ProbeController.class)
class JwtValidationTest extends IntegrationTest {

	@Autowired
	AccessTokenIssuer issuer;

	@Autowired
	SigningKeyService signingKeys;

	@Autowired
	UserRepository users;

	// --- issuance ---

	@Test
	void issuedTokenHasExactClaimsAndKid() throws Exception {
		User agent = newUser(Role.AGENT);
		SignedJWT jwt = SignedJWT.parse(issuer.issue(agent).token());
		RSAKey active = signingKeys.activeKey();

		assertThat(jwt.verify(new RSASSAVerifier(active.toPublicJWK()))).isTrue();
		assertThat(jwt.getHeader().getKeyID()).isEqualTo(active.getKeyID());
		assertThat(jwt.getHeader().getType().getType()).isEqualTo("JWT");
		JWTClaimsSet claims = jwt.getJWTClaimsSet();
		assertThat(claims.getClaims().keySet()).containsExactlyInAnyOrder("sub", "roles", "jti", "iat", "exp", "iss",
				"aud");
		assertThat(claims.getSubject()).isEqualTo(agent.getId().toString());
		assertThat(claims.getStringListClaim("roles")).containsExactly("AGENT");
		assertThat(claims.getAudience()).containsExactly(TestTokens.AUDIENCE);
		assertThat(claims.getIssuer()).isEqualTo(TestTokens.ISSUER);
		assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
			.isEqualTo(Duration.ofMinutes(15));
	}

	@Test
	void jtiIsUniquePerToken() throws Exception {
		User agent = newUser(Role.AGENT);

		assertThat(jti(issuer.issue(agent).token())).isNotEqualTo(jti(issuer.issue(agent).token()));
	}

	// --- accepted ---

	@Test
	void validTokenReturns200WithSubAndRoles() {
		User agent = newUser(Role.AGENT);

		callMe(issuer.issue(agent).token()).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.sub").isEqualTo(agent.getId().toString())
			.jsonPath("$.authorities[0]").isEqualTo("ROLE_AGENT");
	}

	@Test
	void tokenFromRetiredButPublishedKeyAccepted() {
		String token = issuer.issue(newUser(Role.AGENT)).token();
		signingKeys.rotate();

		callMe(token).expectStatus().isOk();
	}

	// --- rejected with 401 ---

	@Test
	void missingTokenReturns401() {
		client.get().uri("/api/test/me").exchange().expectStatus().isUnauthorized();
	}

	@Test
	void expiredTokenReturns401() {
		String token = issuer.issue(newUser(Role.AGENT)).token();
		clock.advance(Duration.ofMinutes(16));

		expect401(callMe(token));
	}

	@Test
	void tamperedSignatureReturns401() {
		String token = issuer.issue(newUser(Role.AGENT)).token();
		char last = token.charAt(token.length() - 2);
		String tampered = token.substring(0, token.length() - 2) + (last == 'A' ? 'B' : 'A') + token.charAt(token.length() - 1);

		expect401(callMe(tampered));
	}

	@Test
	void tamperedPayloadReturns401() throws Exception {
		String[] parts = issuer.issue(newUser(Role.AGENT)).token().split("\\.");
		String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]))
			.replace("\"AGENT\"", "\"ADMIN\"");
		String forged = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding()
			.encodeToString(payload.getBytes()) + "." + parts[2];

		expect401(callMe(forged));
	}

	@Test
	void wrongIssuerReturns401() {
		JWTClaimsSet claims = TestTokens.validClaims(newUser(Role.AGENT).getId().toString(), "AGENT", clock.instant())
			.issuer("https://evil.example")
			.build();

		expect401(callMe(TestTokens.sign(signingKeys.activeKey(), claims)));
	}

	@Test
	void wrongAudienceReturns401() {
		JWTClaimsSet claims = TestTokens.validClaims(newUser(Role.AGENT).getId().toString(), "AGENT", clock.instant())
			.audience("some-other-api")
			.build();

		expect401(callMe(TestTokens.sign(signingKeys.activeKey(), claims)));
	}

	@Test
	void tokenWithoutExpiryReturns401() {
		JWTClaimsSet claims = TestTokens.validClaims(newUser(Role.AGENT).getId().toString(), "AGENT", clock.instant())
			.expirationTime(null)
			.build();

		expect401(callMe(TestTokens.sign(signingKeys.activeKey(), claims)));
	}

	@Test
	void tokenFromKeyRemovedFromJwksReturns401() {
		RSAKey oldKey = signingKeys.activeKey();
		signingKeys.rotate();
		// Past the 15 min TTL + 5 min buffer: the old key is no longer published.
		clock.advance(Duration.ofMinutes(21));
		// A freshly dated token, so only the key (not expiry) can be the reason for rejection.
		JWTClaimsSet claims = TestTokens.validClaims(newUser(Role.AGENT).getId().toString(), "AGENT", clock.instant())
			.build();

		expect401(callMe(TestTokens.sign(oldKey, claims)));
	}

	@Test
	void tokenSignedByUnknownKeyReturns401() {
		JWTClaimsSet claims = TestTokens.validClaims(newUser(Role.AGENT).getId().toString(), "AGENT", clock.instant())
			.build();

		expect401(callMe(TestTokens.sign(TestTokens.unknownKey(), claims)));
	}

	// --- authorization ---

	@Test
	void agentCallingAdminOnlyEndpointReturns403() {
		client.get()
			.uri("/api/test/admin-only")
			.header("Authorization", "Bearer " + issuer.issue(newUser(Role.AGENT)).token())
			.exchange()
			.expectStatus()
			.isForbidden()
			.expectBody()
			.jsonPath("$.status").isEqualTo(403);
	}

	@Test
	void adminCallingAdminOnlyEndpointReturns200() {
		client.get()
			.uri("/api/test/admin-only")
			.header("Authorization", "Bearer " + issuer.issue(newUser(Role.ADMIN)).token())
			.exchange()
			.expectStatus()
			.isOk();
	}

	@Test
	void badBearerHeaderDoesNotBlockPublicAuthEndpoints() {
		client.get()
			.uri("/.well-known/jwks.json")
			.header("Authorization", "Bearer not-a-real-token")
			.exchange()
			.expectStatus()
			.isOk();
	}

	// --- helpers ---

	private ResponseSpec callMe(String token) {
		return client.get().uri("/api/test/me").header("Authorization", "Bearer " + token).exchange();
	}

	private static void expect401(ResponseSpec response) {
		response.expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.status").isEqualTo(401)
			.jsonPath("$.detail").isEqualTo("Authentication is required");
	}

	private User newUser(Role role) {
		return users.save(new User("user-" + UUID.randomUUID() + "@helpdesk.test", "unused", role, clock.instant()));
	}

	private static String jti(String token) throws Exception {
		return SignedJWT.parse(token).getJWTClaimsSet().getJWTID();
	}

	@RestController
	static class ProbeController {

		@GetMapping("/api/test/me")
		Map<String, Object> me(@AuthenticationPrincipal Jwt jwt,
				org.springframework.security.core.Authentication authentication) {
			return Map.of("sub", jwt.getSubject(), "authorities",
					authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
		}

		@GetMapping("/api/test/admin-only")
		@PreAuthorize("hasRole('ADMIN')")
		String adminOnly() {
			return "ok";
		}

	}

}
