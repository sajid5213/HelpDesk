package com.helpdesk.ticket;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.helpdesk.common.IntegrationTest;
import com.helpdesk.user.Role;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TicketFixtures.class)
class StatsAndAutoCloseTest extends IntegrationTest {

	@Autowired
	TicketFixtures fixtures;

	@Autowired
	StatsService statsService;

	@Autowired
	AutoCloseService autoClose;

	// --- stats ---

	@Test
	void statsCountsChangeWithNewTickets() {
		StatsService.Stats before = statsService.current();
		Ticket refund = fixtures.unassigned();
		refund.changeCategory(TicketCategory.REFUND_REQUEST, clock.instant());
		fixtures.save(refund);
		Ticket answered = fixtures.unassigned();
		clock.advance(Duration.ofMinutes(30));
		answered.replied(clock.instant());
		fixtures.save(answered);

		StatsService.Stats after = statsService.current();

		assertThat(after.total()).isEqualTo(before.total() + 2);
		assertThat(after.byStatus().get(TicketStatus.OPEN)).isEqualTo(before.byStatus().get(TicketStatus.OPEN) + 1);
		assertThat(after.byStatus().get(TicketStatus.RESOLVED))
			.isEqualTo(before.byStatus().get(TicketStatus.RESOLVED) + 1);
		assertThat(after.byCategory().get("REFUND_REQUEST")).isEqualTo(before.byCategory().get("REFUND_REQUEST") + 1);
		assertThat(after.byCategory().get("UNCLASSIFIED")).isEqualTo(before.byCategory().get("UNCLASSIFIED") + 1);
		assertThat(after.agentAnswered()).isEqualTo(before.agentAnswered() + 1);
		assertThat(after.avgFirstResponseMinutes()).isNotNull().isPositive();
	}

	@Test
	void statsEndpointIsAdminOnly() {
		client.get()
			.uri("/api/stats")
			.header("Authorization", testUsers.bearer(testUsers.create(Role.ADMIN)))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.byStatus.OPEN").exists()
			.jsonPath("$.byCategory.UNCLASSIFIED").exists();
		client.get()
			.uri("/api/stats")
			.header("Authorization", testUsers.bearer(testUsers.create(Role.AGENT)))
			.exchange()
			.expectStatus()
			.isForbidden();
	}

	// --- auto-close ---

	@Test
	void resolvedTicketClosesAfterSevenDays() {
		Ticket ticket = fixtures.unassigned();
		ticket.resolve(clock.instant());
		fixtures.save(ticket);

		clock.advance(Duration.ofDays(6));
		autoClose.closeStaleResolvedTickets();
		assertThat(fixtures.reload(ticket).getStatus()).isEqualTo(TicketStatus.RESOLVED);

		clock.advance(Duration.ofDays(1).plusMinutes(1));
		autoClose.closeStaleResolvedTickets();
		Ticket closed = fixtures.reload(ticket);
		assertThat(closed.getStatus()).isEqualTo(TicketStatus.CLOSED);
		assertThat(closed.getClosedAt()).isNotNull();
	}

	@Test
	void reopenedTicketIsNotClosed() {
		Ticket ticket = fixtures.unassigned();
		ticket.resolve(clock.instant());
		fixtures.save(ticket);
		clock.advance(Duration.ofDays(3));
		Ticket reopened = fixtures.reload(ticket);
		reopened.receivedMessage(clock.instant());
		fixtures.save(reopened);

		clock.advance(Duration.ofDays(8));
		autoClose.closeStaleResolvedTickets();

		assertThat(fixtures.reload(ticket).getStatus()).isEqualTo(TicketStatus.OPEN);
	}

}
