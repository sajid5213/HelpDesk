package com.helpdesk.auth;

import org.junit.jupiter.api.Test;

import com.helpdesk.common.IntegrationTest;

import org.springframework.http.MediaType;

class SecurityBaselineTest extends IntegrationTest {

	@Test
	void healthIsPublic() {
		management().get().uri("/actuator/health").exchange().expectStatus().isOk();
	}

	@Test
	void apiWithoutTokenReturns401ProblemDetail() {
		client.get()
			.uri("/api/anything")
			.exchange()
			.expectStatus()
			.isUnauthorized()
			.expectHeader()
			.valueEquals("WWW-Authenticate", "Bearer")
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody()
			.jsonPath("$.status").isEqualTo(401)
			.jsonPath("$.title").isEqualTo("Unauthorized")
			.jsonPath("$.instance").isEqualTo("/api/anything");
	}

	@Test
	void noSessionCookieIsIssued() {
		client.get().uri("/api/anything").exchange().expectHeader().doesNotExist("Set-Cookie");
	}

}
