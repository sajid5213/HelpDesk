package com.helpdesk.user;

import org.junit.jupiter.api.Test;

import com.helpdesk.common.IntegrationTest;

class MeControllerTest extends IntegrationTest {

	@Test
	void agentSeesOwnAccountWithoutSecrets() {
		User agent = testUsers.create(Role.AGENT);

		client.get()
			.uri("/api/me")
			.header("Authorization", testUsers.bearer(agent))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.id").isEqualTo(agent.getId().toString())
			.jsonPath("$.email").isEqualTo(agent.getEmail())
			.jsonPath("$.role").isEqualTo("AGENT")
			.jsonPath("$.passwordHash").doesNotExist();
	}

	@Test
	void adminSeesAdminRole() {
		client.get()
			.uri("/api/me")
			.header("Authorization", testUsers.bearer(testUsers.create(Role.ADMIN)))
			.exchange()
			.expectBody()
			.jsonPath("$.role").isEqualTo("ADMIN");
	}

	@Test
	void withoutTokenReturns401() {
		client.get().uri("/api/me").exchange().expectStatus().isUnauthorized();
	}

}
