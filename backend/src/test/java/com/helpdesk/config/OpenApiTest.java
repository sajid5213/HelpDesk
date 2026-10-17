package com.helpdesk.config;

import org.junit.jupiter.api.Test;

import com.helpdesk.common.IntegrationTest;

class OpenApiTest extends IntegrationTest {

	@Test
	void apiDocsArePublicAndListAuthAndTicketEndpoints() {
		client.get()
			.uri("/v3/api-docs")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.paths['/auth/token']").exists()
			.jsonPath("$.paths['/api/tickets']").exists()
			.jsonPath("$.paths['/api/users']").exists()
			.jsonPath("$.components.securitySchemes.bearerAuth.scheme").isEqualTo("bearer");
	}

	@Test
	void swaggerUiIsReachable() {
		client.get().uri("/swagger-ui/index.html").exchange().expectStatus().isOk();
	}

}
