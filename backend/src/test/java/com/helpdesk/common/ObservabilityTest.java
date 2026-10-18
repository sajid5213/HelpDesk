package com.helpdesk.common;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;

import com.helpdesk.ai.Classification;
import com.helpdesk.ai.ReplyDraft;
import com.helpdesk.kb.KbArticle;
import com.helpdesk.kb.KbArticleRepository;
import com.helpdesk.ticket.InboundEmailRequest;
import com.helpdesk.ticket.InboundEmailService;
import com.helpdesk.ticket.TicketCategory;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/** Metrics live on the private management port, count what matters, and hold no personal data. */
class ObservabilityTest extends IntegrationTest {

	@Autowired
	MeterRegistry registry;

	@Autowired
	InboundEmailService inbound;

	@Autowired
	KbArticleRepository articles;

	@Test
	void prometheusIsOnTheManagementPortWithoutLogin() {
		String scrape = scrape();

		assertThat(scrape).contains("helpdesk_tickets_opened_total", "helpdesk_ai_outcomes_total",
				"helpdesk_auth_events_total{type=\"TOKEN_REUSE_DETECTED\"}", "helpdesk_jobs_due",
				"dbscheduler_task_completions", "hikaricp_connections");
	}

	@Test
	void prometheusIsNotOnThePublicPort() {
		client.get().uri("/actuator/prometheus").exchange().expectStatus().isUnauthorized();
		client.get()
			.uri("/actuator/prometheus")
			.header("Authorization", testUsers.bearer(testUsers.create(Role.ADMIN)))
			.exchange()
			.expectStatus()
			.isNotFound();
	}

	@Test
	void failedLoginIsCountedWithoutTheEmail() {
		User agent = testUsers.create(Role.AGENT);
		double before = count(HelpdeskMetrics.AUTH_EVENTS, "type", "LOGIN_FAILURE");

		client.post()
			.uri("/auth/token")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("email", agent.getEmail(), "password", "not-the-password-123"))
			.exchange()
			.expectStatus()
			.isUnauthorized();

		assertThat(count(HelpdeskMetrics.AUTH_EVENTS, "type", "LOGIN_FAILURE")).isEqualTo(before + 1);
		assertThat(scrape()).doesNotContain(agent.getEmail()).doesNotContain(agent.getId().toString());
	}

	@Test
	void autoResolvedTicketCountsItsOutcomeAndClaudeCalls() {
		Long articleId = articles.save(new KbArticle("Video does not load", "Clear the cache.", clock.instant()))
			.getId();
		fakeAi.answer(new Classification(TicketCategory.TECHNICAL_QUESTION, "Video will not play.", false),
				new ReplyDraft(true, List.of(articleId), "Hi, clear your browser cache and reload."));
		double opened = count(HelpdeskMetrics.TICKETS_OPENED);
		double resolved = count(HelpdeskMetrics.AI_OUTCOMES, "outcome", "auto_resolved");
		long classifyCalls = calls("classify");

		inbound.receive(new InboundEmailRequest("sam.metrics@example.com", "Sam", "Video will not load",
				"The lesson video stays black.", null));

		assertThat(count(HelpdeskMetrics.TICKETS_OPENED)).isEqualTo(opened + 1);
		await(() -> count(HelpdeskMetrics.AI_OUTCOMES, "outcome", "auto_resolved") == resolved + 1);
		assertThat(calls("classify")).isEqualTo(classifyCalls + 1);
		assertThat(scrape()).doesNotContain("sam.metrics@example.com");
	}

	private String scrape() {
		return management().get()
			.uri("/actuator/prometheus")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
	}

	private double count(String name, String... tags) {
		var counter = registry.find(name).tags(tags).counter();
		return counter == null ? 0 : counter.count();
	}

	private long calls(String operation) {
		var timer = registry.find("helpdesk.ai.call").tags("operation", operation, "outcome", "success").timer();
		return timer == null ? 0 : timer.count();
	}

	private static void await(java.util.function.BooleanSupplier condition) {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
		while (!condition.getAsBoolean()) {
			assertThat(Instant.now()).as("condition within 15s").isBefore(deadline);
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(ex);
			}
		}
	}

}
