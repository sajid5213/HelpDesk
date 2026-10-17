package com.helpdesk.auth;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

import com.helpdesk.audit.AuthEvent;
import com.helpdesk.audit.AuthEventRepository;
import com.helpdesk.audit.AuthEventType;
import com.helpdesk.common.IntegrationTest;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFlowTest extends IntegrationTest {

	private static final String PASSWORD = "correct-horse-battery";

	private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
	};

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Autowired
	AuthEventRepository events;

	// --- login ---

	@Test
	void validLoginReturnsTokensAndAuditsSuccess() {
		User user = newUser();

		Map<String, Object> body = login(user.getEmail(), PASSWORD).expectStatus().isOk().returnResult(JSON_OBJECT).getResponseBody();

		assertThat(body.get("tokenType")).isEqualTo("Bearer");
		assertThat(body.get("expiresIn")).isEqualTo(900);
		assertThat((String) body.get("accessToken")).isNotBlank();
		assertThat((String) body.get("refreshToken")).isNotBlank();
		assertThat(eventTypes(user)).containsExactly(AuthEventType.LOGIN_SUCCESS);
	}

	@Test
	void accessTokenFromLoginIsAcceptedByTheApi() {
		User user = newUser();
		String accessToken = (String) tokens(user).get("accessToken");

		// Any authenticated path; 404 (not 401) proves the token was accepted.
		client.get()
			.uri("/api/does-not-exist")
			.header("Authorization", "Bearer " + accessToken)
			.exchange()
			.expectStatus()
			.isNotFound();
	}

	@Test
	void wrongPasswordReturns401AndAuditsFailure() {
		User user = newUser();

		login(user.getEmail(), "wrong-password").expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.error").isEqualTo("invalid_credentials")
			.jsonPath("$.detail").isEqualTo("Email or password is incorrect");
		assertThat(eventTypes(user)).containsExactly(AuthEventType.LOGIN_FAILURE);
	}

	@Test
	void unknownEmailReturnsSame401Body() {
		String wrongPassword = login(newUser().getEmail(), "wrong-password").returnResult(String.class).getResponseBody();
		String unknownEmail = login("nobody-" + UUID.randomUUID() + "@helpdesk.test", "whatever")
			.expectStatus()
			.isUnauthorized()
			.returnResult(String.class)
			.getResponseBody();

		assertThat(withoutInstant(unknownEmail)).isEqualTo(withoutInstant(wrongPassword));
	}

	@Test
	void deactivatedUserCannotLogIn() {
		User user = newUser();
		user.setActive(false);
		users.save(user);

		login(user.getEmail(), PASSWORD).expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.error").isEqualTo("invalid_credentials");
	}

	@Test
	void loginRequiresEmailAndPassword() {
		client.post()
			.uri("/auth/token")
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"email\":\"\"}")
			.exchange()
			.expectStatus()
			.isBadRequest();
	}

	// --- refresh tokens ---

	@Test
	void refreshTokenStoredOnlyAsHash() {
		User user = newUser();
		String raw = (String) tokens(user).get("refreshToken");

		List<RefreshToken> stored = refreshTokens.findByUserId(user.getId());
		assertThat(stored).hasSize(1);
		assertThat(stored.get(0).getTokenHash()).hasSize(64).isNotEqualTo(raw).isEqualTo(RefreshTokenService.hash(raw));
	}

	@Test
	void refreshRotatesAndOldTokenStopsWorking() {
		User user = newUser();
		String first = (String) tokens(user).get("refreshToken");

		Map<String, Object> refreshed = refresh(first).expectStatus().isOk().returnResult(JSON_OBJECT).getResponseBody();
		String second = (String) refreshed.get("refreshToken");

		assertThat(second).isNotEqualTo(first);
		assertThat((String) refreshed.get("accessToken")).isNotBlank();
		assertThat(eventTypes(user)).contains(AuthEventType.TOKEN_REFRESH);
		refresh(second).expectStatus().isOk();
	}

	@Test
	void reusingRotatedTokenRevokesAllUserTokensAndAudits() {
		User user = newUser();
		String stolen = (String) tokens(user).get("refreshToken");
		String current = (String) refresh(stolen).returnResult(JSON_OBJECT).getResponseBody().get("refreshToken");
		String otherDevice = (String) tokens(user).get("refreshToken");

		refresh(stolen).expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.error").isEqualTo("invalid_refresh_token");

		refresh(current).expectStatus().isUnauthorized();
		refresh(otherDevice).expectStatus().isUnauthorized();
		assertThat(eventTypes(user)).contains(AuthEventType.TOKEN_REUSE_DETECTED);
	}

	@Test
	void concurrentRefreshWithSameTokenRevokesFamily() throws Exception {
		User user = newUser();
		String token = (String) tokens(user).get("refreshToken");
		CountDownLatch start = new CountDownLatch(1);

		List<CompletableFuture<Integer>> attempts = List.of(1, 2).stream().map((i) -> CompletableFuture.supplyAsync(() -> {
			try {
				start.await();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return refresh(token).returnResult(String.class).getStatus().value();
		})).toList();
		start.countDown();
		List<Integer> statuses = attempts.stream().map(CompletableFuture::join).toList();

		assertThat(statuses).containsExactlyInAnyOrder(200, 401);
		assertThat(refreshTokens.findByUserId(user.getId())).allSatisfy((t) -> assertThat(t.getRevokedAt()).isNotNull());
	}

	@Test
	void expiredRefreshTokenReturns401() {
		String token = (String) tokens(newUser()).get("refreshToken");
		clock.advance(Duration.ofDays(7).plusSeconds(1));

		refresh(token).expectStatus().isUnauthorized();
	}

	@Test
	void unknownRefreshTokenReturns401() {
		refresh("not-a-real-token").expectStatus().isUnauthorized();
	}

	@Test
	void expiredAccessTokenHeaderDoesNotBlockRefresh() {
		Map<String, Object> pair = tokens(newUser());
		clock.advance(Duration.ofMinutes(16));

		client.post()
			.uri("/auth/refresh")
			.header("Authorization", "Bearer " + pair.get("accessToken"))
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("refreshToken", pair.get("refreshToken")))
			.exchange()
			.expectStatus()
			.isOk();
	}

	// --- logout ---

	@Test
	void logoutThenRefreshReturns401() {
		User user = newUser();
		String token = (String) tokens(user).get("refreshToken");

		client.post()
			.uri("/auth/logout")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("refreshToken", token))
			.exchange()
			.expectStatus()
			.isNoContent();

		refresh(token).expectStatus().isUnauthorized();
		assertThat(eventTypes(user)).contains(AuthEventType.LOGOUT);
	}

	@Test
	void logoutWithUnknownTokenStillReturns204() {
		client.post()
			.uri("/auth/logout")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("refreshToken", "unknown"))
			.exchange()
			.expectStatus()
			.isNoContent();
	}

	@Test
	void auditRowsNeverContainSecrets() {
		User user = newUser();
		Map<String, Object> pair = tokens(user);
		refresh((String) pair.get("refreshToken"));

		assertThat(events.findByUserIdOrderByIdAsc(user.getId())).isNotEmpty().allSatisfy((event) -> {
			assertThat(String.valueOf(event.getIp())).doesNotContain(PASSWORD);
			assertThat(event.toString()).doesNotContain((String) pair.get("refreshToken"));
		});
	}

	// --- helpers ---

	private User newUser() {
		return users.save(new User("user-" + UUID.randomUUID() + "@helpdesk.test", passwordEncoder.encode(PASSWORD),
				Role.AGENT, clock.instant()));
	}

	private ResponseSpec login(String email, String password) {
		return client.post()
			.uri("/auth/token")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("email", email, "password", password))
			.exchange();
	}

	private Map<String, Object> tokens(User user) {
		return login(user.getEmail(), PASSWORD).expectStatus().isOk().returnResult(JSON_OBJECT).getResponseBody();
	}

	private ResponseSpec refresh(String refreshToken) {
		return client.post()
			.uri("/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("refreshToken", refreshToken))
			.exchange();
	}

	private List<AuthEventType> eventTypes(User user) {
		return events.findByUserIdOrderByIdAsc(user.getId()).stream().map(AuthEvent::getType).toList();
	}

	private static String withoutInstant(String problemJson) {
		return problemJson.replaceAll("\"instance\":\"[^\"]*\"", "");
	}

}
