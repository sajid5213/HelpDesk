package com.helpdesk.kb;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.helpdesk.common.IntegrationTest;
import com.helpdesk.user.Role;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;

class KbArticleTest extends IntegrationTest {

	private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
	};

	@Test
	void adminCanCreateReadUpdateAndDelete() {
		String admin = testUsers.bearer(testUsers.create(Role.ADMIN));

		Map<String, Object> created = client.post()
			.uri("/api/kb/articles")
			.header("Authorization", admin)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("title", "  Refund policy ", "body", "Full refund within 14 days."))
			.exchange()
			.expectStatus()
			.isCreated()
			.returnResult(JSON_OBJECT)
			.getResponseBody();
		String path = "/api/kb/articles/" + created.get("id");

		client.get().uri(path).header("Authorization", admin).exchange()
			.expectStatus().isOk()
			.expectBody().jsonPath("$.title").isEqualTo("Refund policy");

		client.put()
			.uri(path)
			.header("Authorization", admin)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("title", "Refund policy", "body", "Full refund within 30 days."))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.body").isEqualTo("Full refund within 30 days.");

		client.delete().uri(path).header("Authorization", admin).exchange().expectStatus().isNoContent();
		client.get().uri(path).header("Authorization", admin).exchange().expectStatus().isNotFound();
	}

	@Test
	void agentCanReadButNotWrite() {
		String admin = testUsers.bearer(testUsers.create(Role.ADMIN));
		String agent = testUsers.bearer(testUsers.create(Role.AGENT));
		Object id = client.post()
			.uri("/api/kb/articles")
			.header("Authorization", admin)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("title", "Certificates", "body", "Certificates arrive within 2 days."))
			.exchange()
			.returnResult(JSON_OBJECT)
			.getResponseBody()
			.get("id");

		client.get().uri("/api/kb/articles").header("Authorization", agent).exchange().expectStatus().isOk();
		client.get().uri("/api/kb/articles/" + id).header("Authorization", agent).exchange().expectStatus().isOk();
		client.post()
			.uri("/api/kb/articles")
			.header("Authorization", agent)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("title", "x", "body", "y"))
			.exchange()
			.expectStatus()
			.isForbidden();
		client.delete().uri("/api/kb/articles/" + id).header("Authorization", agent).exchange()
			.expectStatus().isForbidden();
	}

	@Test
	void blankTitleIsRejected() {
		client.post()
			.uri("/api/kb/articles")
			.header("Authorization", testUsers.bearer(testUsers.create(Role.ADMIN)))
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("title", " ", "body", "y"))
			.exchange()
			.expectStatus()
			.isBadRequest();
	}

}
