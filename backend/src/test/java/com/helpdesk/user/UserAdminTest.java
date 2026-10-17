package com.helpdesk.user;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import com.helpdesk.audit.AuthEvent;
import com.helpdesk.audit.AuthEventRepository;
import com.helpdesk.audit.AuthEventType;
import com.helpdesk.common.IntegrationTest;
import com.helpdesk.common.TestUsers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

import static org.assertj.core.api.Assertions.assertThat;

class UserAdminTest extends IntegrationTest {

	private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
	};

	private static final String NEW_PASSWORD = "a-brand-new-password";

	@Autowired
	UserRepository users;

	@Autowired
	AuthEventRepository events;

	// --- access ---

	@Test
	void withoutTokenReturns401() {
		client.get().uri("/api/users").exchange().expectStatus().isUnauthorized();
	}

	@Test
	void agentGets403OnEveryEndpoint() {
		String agent = testUsers.bearer(testUsers.create(Role.AGENT));
		UUID someone = testUsers.create(Role.AGENT).getId();

		client.get().uri("/api/users").header("Authorization", agent).exchange().expectStatus().isForbidden();
		client.get().uri("/api/users/" + someone).header("Authorization", agent).exchange().expectStatus().isForbidden();
		post(agent, Map.of("email", TestUsers.randomEmail(), "password", NEW_PASSWORD)).expectStatus().isForbidden();
		patch(agent, someone, Map.of("active", false)).expectStatus().isForbidden();
	}

	// --- create ---

	@Test
	void adminCreatesAgentWhoCanLogIn() {
		String email = TestUsers.randomEmail();

		Map<String, Object> created = post(adminBearer(), Map.of("email", email, "password", NEW_PASSWORD))
			.expectStatus()
			.isCreated()
			.expectHeader()
			.valueMatches("Location", "/api/users/[0-9a-f-]{36}")
			.returnResult(JSON_OBJECT)
			.getResponseBody();

		assertThat(created).containsEntry("email", email).containsEntry("role", "AGENT").containsEntry("active", true);
		assertThat(created).doesNotContainKeys("passwordHash", "password");
		login(email, NEW_PASSWORD).expectStatus().isOk();
	}

	@Test
	void adminCanCreateAnotherAdmin() {
		post(adminBearer(), Map.of("email", TestUsers.randomEmail(), "password", NEW_PASSWORD, "role", "ADMIN"))
			.expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.role").isEqualTo("ADMIN");
	}

	@Test
	void duplicateEmailInDifferentCaseReturns409() {
		User existing = testUsers.create(Role.AGENT);

		post(adminBearer(), Map.of("email", existing.getEmail().toUpperCase(), "password", NEW_PASSWORD))
			.expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.error").isEqualTo("email_taken");
	}

	@Test
	void shortPasswordReturns400() {
		post(adminBearer(), Map.of("email", TestUsers.randomEmail(), "password", "short")).expectStatus().isBadRequest();
	}

	@Test
	void invalidEmailReturns400() {
		post(adminBearer(), Map.of("email", "not-an-email", "password", NEW_PASSWORD)).expectStatus().isBadRequest();
	}

	// --- list and get ---

	@Test
	void listShowsNewestFirstWithoutHashes() {
		String admin = adminBearer();
		User newest = testUsers.create(Role.AGENT);

		Map<String, Object> page = client.get()
			.uri("/api/users?size=5")
			.header("Authorization", admin)
			.exchange()
			.expectStatus()
			.isOk()
			.returnResult(JSON_OBJECT)
			.getResponseBody();

		List<Map<String, Object>> content = content(page);
		assertThat(content.get(0)).containsEntry("id", newest.getId().toString()).doesNotContainKey("passwordHash");
		assertThat(page).containsKey("page");
	}

	@Test
	void pageSizeIsCappedAt100() {
		client.get()
			.uri("/api/users?size=5000")
			.header("Authorization", adminBearer())
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.page.size").isEqualTo(100);
	}

	@Test
	void unknownUserReturns404() {
		client.get()
			.uri("/api/users/" + UUID.randomUUID())
			.header("Authorization", adminBearer())
			.exchange()
			.expectStatus()
			.isNotFound();
	}

	// --- update ---

	@Test
	void passwordResetWorksAndEndsExistingSessions() {
		User agent = testUsers.create(Role.AGENT);
		String refresh = refreshTokenOf(agent.getEmail(), TestUsers.PASSWORD);

		patch(adminBearer(), agent.getId(), Map.of("password", NEW_PASSWORD)).expectStatus().isOk();

		refresh(refresh).expectStatus().isUnauthorized();
		login(agent.getEmail(), TestUsers.PASSWORD).expectStatus().isUnauthorized();
		login(agent.getEmail(), NEW_PASSWORD).expectStatus().isOk();
	}

	@Test
	void emailChangeIsNormalizedAndCollisionReturns409() {
		User agent = testUsers.create(Role.AGENT);
		User other = testUsers.create(Role.AGENT);
		String newEmail = TestUsers.randomEmail();

		patch(adminBearer(), agent.getId(), Map.of("email", "  " + newEmail.toUpperCase() + " ")).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.email").isEqualTo(newEmail);

		patch(adminBearer(), agent.getId(), Map.of("email", other.getEmail())).expectStatus().isEqualTo(409);
	}

	@Test
	void roleChangeEndsSessionsAndNextTokenHasNewRole() throws Exception {
		User agent = testUsers.create(Role.AGENT);
		String refresh = refreshTokenOf(agent.getEmail(), TestUsers.PASSWORD);

		patch(adminBearer(), agent.getId(), Map.of("role", "ADMIN")).expectStatus().isOk();

		refresh(refresh).expectStatus().isUnauthorized();
		String accessToken = (String) login(agent.getEmail(), TestUsers.PASSWORD).returnResult(JSON_OBJECT)
			.getResponseBody()
			.get("accessToken");
		assertThat(SignedJWT.parse(accessToken).getJWTClaimsSet().getStringListClaim("roles")).containsExactly("ADMIN");
	}

	// --- deactivate and reactivate ---

	@Test
	void deactivatingUserRevokesRefreshTokens() {
		User agent = testUsers.create(Role.AGENT);
		String refresh = refreshTokenOf(agent.getEmail(), TestUsers.PASSWORD);

		patch(adminBearer(), agent.getId(), Map.of("active", false)).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.active").isEqualTo(false);

		refresh(refresh).expectStatus().isUnauthorized();
		login(agent.getEmail(), TestUsers.PASSWORD).expectStatus().isUnauthorized();
	}

	@Test
	void reactivatingClearsLockoutAndAllowsLogin() {
		User agent = testUsers.create(Role.AGENT);
		for (int i = 0; i < 5; i++) {
			login(agent.getEmail(), "wrong-password");
		}
		String admin = adminBearer();
		patch(admin, agent.getId(), Map.of("active", false)).expectStatus().isOk();

		patch(admin, agent.getId(), Map.of("active", true)).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.active").isEqualTo(true)
			.jsonPath("$.locked").isEqualTo(false);

		clock.advance(Duration.ofSeconds(61)); // past the per-account rate limit window, not the lock
		login(agent.getEmail(), TestUsers.PASSWORD).expectStatus().isOk();
	}

	// --- self-protection ---

	@Test
	void adminCannotDeactivateOrDemoteThemselves() {
		User admin = testUsers.create(Role.ADMIN);
		String bearer = testUsers.bearer(admin);

		patch(bearer, admin.getId(), Map.of("active", false)).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.error").isEqualTo("cannot_modify_self");
		patch(bearer, admin.getId(), Map.of("role", "AGENT")).expectStatus().isEqualTo(409);
		assertThat(users.findById(admin.getId()).orElseThrow().isActive()).isTrue();
	}

	@Test
	void adminCanChangeOwnPassword() {
		User admin = testUsers.create(Role.ADMIN);

		patch(testUsers.bearer(admin), admin.getId(), Map.of("password", NEW_PASSWORD)).expectStatus().isOk();

		login(admin.getEmail(), NEW_PASSWORD).expectStatus().isOk();
	}

	// --- audit ---

	@Test
	void adminActionsAreAuditedWithActor() {
		User admin = testUsers.create(Role.ADMIN);
		String bearer = testUsers.bearer(admin);
		String email = TestUsers.randomEmail();
		UUID created = UUID.fromString((String) post(bearer, Map.of("email", email, "password", NEW_PASSWORD))
			.returnResult(JSON_OBJECT)
			.getResponseBody()
			.get("id"));

		patch(bearer, created, Map.of("password", "another-new-password", "role", "ADMIN")).expectStatus().isOk();
		patch(bearer, created, Map.of("active", false)).expectStatus().isOk();
		patch(bearer, created, Map.of("active", true)).expectStatus().isOk();

		List<AuthEvent> trail = events.findByUserIdOrderByIdAsc(created);
		assertThat(trail).extracting(AuthEvent::getType)
			.containsExactly(AuthEventType.USER_CREATED, AuthEventType.PASSWORD_CHANGED, AuthEventType.ROLE_CHANGED,
					AuthEventType.USER_DEACTIVATED, AuthEventType.USER_REACTIVATED);
		assertThat(trail).allSatisfy((event) -> assertThat(event.getActorId()).isEqualTo(admin.getId()));
	}

	// --- helpers ---

	private String adminBearer() {
		return testUsers.bearer(testUsers.create(Role.ADMIN));
	}

	private ResponseSpec post(String bearer, Map<String, Object> body) {
		return client.post()
			.uri("/api/users")
			.header("Authorization", bearer)
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.exchange();
	}

	private ResponseSpec patch(String bearer, UUID id, Map<String, Object> body) {
		return client.patch()
			.uri("/api/users/" + id)
			.header("Authorization", bearer)
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.exchange();
	}

	private ResponseSpec login(String email, String password) {
		return client.post()
			.uri("/auth/token")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("email", email, "password", password))
			.exchange();
	}

	private String refreshTokenOf(String email, String password) {
		return (String) login(email, password).expectStatus().isOk().returnResult(JSON_OBJECT).getResponseBody()
			.get("refreshToken");
	}

	private ResponseSpec refresh(String refreshToken) {
		return client.post()
			.uri("/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("refreshToken", refreshToken))
			.exchange();
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> content(Map<String, Object> page) {
		return (List<Map<String, Object>>) page.get("content");
	}

}
