package com.helpdesk.ticket;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns an incoming student email into a ticket, or adds it to the thread it belongs to.
 * A thread is only continued when the reference matches <em>and</em> the sender is the
 * ticket's student, so one student can never write into another student's ticket.
 */
@Service
public class InboundEmailService {

	private static final Pattern SUBJECT_REF = Pattern.compile("\\[#(\\d{1,18})]");

	private final TicketRepository tickets;

	private final TicketMessageRepository messages;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	InboundEmailService(TicketRepository tickets, TicketMessageRepository messages, ApplicationEventPublisher events,
			Clock clock) {
		this.tickets = tickets;
		this.messages = messages;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	public Result receive(InboundEmailRequest email) {
		Instant now = clock.instant();
		Optional<Ticket> thread = reference(email).flatMap(tickets::findById)
			.filter((ticket) -> ticket.isFrom(email.fromEmail()))
			.filter((ticket) -> ticket.getStatus() != TicketStatus.CLOSED);

		boolean newTicket = thread.isEmpty();
		Ticket ticket = thread.orElseGet(
				() -> tickets.save(new Ticket(email.subject(), email.fromEmail(), email.fromName(), now)));
		if (!newTicket) {
			ticket.receivedMessage(now);
		}
		TicketMessage message = messages.save(TicketMessage.fromStudent(ticket.getId(), email.body(), now));
		events.publishEvent(new TicketMessageReceived(ticket.getId(), message.getId(), newTicket));
		return new Result(ticket.getId(), newTicket);
	}

	private static Optional<Long> reference(InboundEmailRequest email) {
		if (email.ticketRef() != null) {
			return Optional.of(email.ticketRef());
		}
		Matcher matcher = SUBJECT_REF.matcher(email.subject());
		return matcher.find() ? Optional.of(Long.parseLong(matcher.group(1))) : Optional.empty();
	}

	public record Result(Long ticketId, boolean newTicket) {
	}

}
