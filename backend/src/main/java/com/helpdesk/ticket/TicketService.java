package com.helpdesk.ticket;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.helpdesk.common.ApiException;
import com.helpdesk.common.CurrentUser;
import com.helpdesk.common.NotFoundException;
import com.helpdesk.email.OutboundEmail;
import com.helpdesk.email.OutboundEmailRequested;
import com.helpdesk.ticket.TicketRequests.UpdateTicketRequest;
import com.helpdesk.ticket.TicketViews.TicketDetail;
import com.helpdesk.ticket.TicketViews.TicketSummary;
import com.helpdesk.user.UserRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Working a ticket: view, claim, reply, note, change status or category, reassign. */
@Service
public class TicketService {

	static final String ALREADY_ASSIGNED = "already_assigned";

	static final String TICKET_CLOSED = "ticket_closed";

	static final String INVALID_TRANSITION = "invalid_transition";

	static final String INVALID_ASSIGNEE = "invalid_assignee";

	private final TicketRepository tickets;

	private final TicketMessageRepository messages;

	private final TicketNoteRepository notes;

	private final UserRepository users;

	private final TicketAccessPolicy access;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	TicketService(TicketRepository tickets, TicketMessageRepository messages, TicketNoteRepository notes,
			UserRepository users, TicketAccessPolicy access, ApplicationEventPublisher events, Clock clock) {
		this.tickets = tickets;
		this.messages = messages;
		this.notes = notes;
		this.users = users;
		this.access = access;
		this.events = events;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public TicketDetail detail(Long id, CurrentUser user) {
		Ticket ticket = find(id);
		access.requireRead(user, ticket);
		return detailOf(ticket);
	}

	/** Takes an unassigned ticket. Exactly one of two agents claiming at once wins. */
	@Transactional
	public TicketSummary claim(Long id, CurrentUser user) {
		Ticket ticket = find(id);
		if (ticket.isAssignedTo(user.id())) {
			return TicketSummary.of(ticket);
		}
		requireNotClosed(ticket);
		if (tickets.claimIfUnassigned(id, user.id(), clock.instant()) == 0) {
			throw new ApiException(HttpStatus.CONFLICT, ALREADY_ASSIGNED, "Another agent already has this ticket");
		}
		return TicketSummary.of(find(id));
	}

	/** Answers the student: stores the message, resolves the ticket, emails after commit. */
	@Transactional
	public TicketDetail reply(Long id, String body, CurrentUser user) {
		Ticket ticket = find(id);
		access.requireUpdate(user, ticket);
		requireNotClosed(ticket);
		Instant now = clock.instant();
		messages.save(TicketMessage.fromAgent(ticket.getId(), user.id(), body, now));
		ticket.replied(now);
		tickets.saveAndFlush(ticket);
		events.publishEvent(new OutboundEmailRequested(
				new OutboundEmail(ticket.getStudentEmail(), replySubject(ticket), body, ticket.getId())));
		return detailOf(ticket);
	}

	@Transactional
	public TicketDetail addNote(Long id, String body, CurrentUser user) {
		Ticket ticket = find(id);
		access.requireUpdate(user, ticket);
		Instant now = clock.instant();
		notes.save(new TicketNote(ticket.getId(), user.id(), body, now));
		ticket.touch(now);
		tickets.saveAndFlush(ticket);
		return detailOf(ticket);
	}

	/** Status moves: OPEN and RESOLVED switch back and forth; either can close; CLOSED is final. */
	@Transactional
	public TicketDetail update(Long id, UpdateTicketRequest request, CurrentUser user) {
		Ticket ticket = find(id);
		access.requireUpdate(user, ticket);
		Instant now = clock.instant();
		if (request.status() != null && request.status() != ticket.getStatus()) {
			requireNotClosed(ticket, INVALID_TRANSITION);
			switch (request.status()) {
				case OPEN -> ticket.reopen(now);
				case RESOLVED -> ticket.resolve(now);
				case CLOSED -> ticket.close(now);
			}
		}
		if (request.category() != null) {
			ticket.changeCategory(request.category(), now);
		}
		tickets.saveAndFlush(ticket);
		return detailOf(ticket);
	}

	/** Admin only (checked by the controller): give the ticket to an agent, or unassign it. */
	@Transactional
	public TicketSummary assign(Long id, UUID assigneeId) {
		Ticket ticket = find(id);
		if (assigneeId != null && users.findById(assigneeId).filter((u) -> u.isActive()).isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_ASSIGNEE, "No active user with id " + assigneeId);
		}
		ticket.assignTo(assigneeId, clock.instant());
		tickets.saveAndFlush(ticket);
		return TicketSummary.of(ticket);
	}

	private Ticket find(Long id) {
		return tickets.findById(id).orElseThrow(() -> new NotFoundException("Ticket " + id + " not found"));
	}

	private TicketDetail detailOf(Ticket ticket) {
		return TicketDetail.of(ticket, messages.findByTicketIdOrderByIdAsc(ticket.getId()),
				notes.findByTicketIdOrderByIdAsc(ticket.getId()));
	}

	private static void requireNotClosed(Ticket ticket) {
		requireNotClosed(ticket, TICKET_CLOSED);
	}

	private static void requireNotClosed(Ticket ticket, String errorCode) {
		if (ticket.getStatus() == TicketStatus.CLOSED) {
			throw new ApiException(HttpStatus.CONFLICT, errorCode, "Ticket " + ticket.getId() + " is closed");
		}
	}

	/** "[#id]" lets the student's reply find its way back to this ticket. */
	static String replySubject(Ticket ticket) {
		return "Re: [#" + ticket.getId() + "] " + ticket.getSubject();
	}

}
