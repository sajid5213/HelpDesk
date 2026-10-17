package com.helpdesk.ticket;

import java.time.Clock;
import java.util.UUID;

import org.springframework.boot.test.context.TestComponent;

/** Creates tickets directly in the database for tests, skipping the inbound email flow. */
@TestComponent
class TicketFixtures {

	private final TicketRepository tickets;

	private final TicketMessageRepository messages;

	private final Clock clock;

	TicketFixtures(TicketRepository tickets, TicketMessageRepository messages, Clock clock) {
		this.tickets = tickets;
		this.messages = messages;
		this.clock = clock;
	}

	Ticket unassigned() {
		return create("Question " + UUID.randomUUID(), null);
	}

	Ticket assignedTo(UUID agentId) {
		return create("Question " + UUID.randomUUID(), agentId);
	}

	Ticket create(String subject, UUID assigneeId) {
		Ticket ticket = new Ticket(subject, "student-" + UUID.randomUUID() + "@example.com", "Sam Student",
				clock.instant());
		if (assigneeId != null) {
			ticket.assignTo(assigneeId, clock.instant());
		}
		Ticket saved = tickets.save(ticket);
		messages.save(TicketMessage.fromStudent(saved.getId(), "Hello, I need help.", clock.instant()));
		return saved;
	}

	Ticket save(Ticket ticket) {
		return tickets.save(ticket);
	}

	Ticket reload(Ticket ticket) {
		return tickets.findById(ticket.getId()).orElseThrow();
	}

}
