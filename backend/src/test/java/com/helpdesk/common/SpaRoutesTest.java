package com.helpdesk.common;

import org.junit.jupiter.api.Test;

import com.helpdesk.user.Role;

import static org.assertj.core.api.Assertions.assertThat;

/** The packaged React app: its pages load without a token, while the data stays behind /api. */
class SpaRoutesTest extends IntegrationTest {

	@Test
	void reloadingAReactRouteServesTheApp() {
		for (String path : new String[] { "/", "/login", "/tickets", "/tickets/11" }) {
			client.get()
				.uri(path)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody(String.class)
				.value((html) -> assertThat(html).as(path).contains("<div id=\"root\">"));
		}
	}

	@Test
	void staticAssetsAreNotBehindLogin() {
		client.get().uri("/assets/missing.js").exchange().expectStatus().isNotFound();
		client.get().uri("/favicon.svg").exchange().expectStatus().isNotFound();
	}

	@Test
	void apiStillNeedsAToken() {
		client.get().uri("/api/tickets").exchange().expectStatus().isUnauthorized();
	}

	@Test
	void unknownPagesAreNotTheApp() {
		client.get()
			.uri("/tickets/abc")
			.header("Authorization", testUsers.bearer(testUsers.create(Role.AGENT)))
			.exchange()
			.expectStatus()
			.isNotFound();
		client.get().uri("/tickets/abc").exchange().expectStatus().isUnauthorized();
	}

}
