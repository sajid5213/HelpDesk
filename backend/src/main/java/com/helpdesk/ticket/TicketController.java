package com.helpdesk.ticket;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import com.helpdesk.common.CurrentUser;
import com.helpdesk.ticket.TicketRequests.AssignRequest;
import com.helpdesk.ticket.TicketRequests.NoteRequest;
import com.helpdesk.ticket.TicketRequests.ReplyRequest;
import com.helpdesk.ticket.TicketRequests.UpdateTicketRequest;
import com.helpdesk.ticket.TicketViews.TicketDetail;
import com.helpdesk.ticket.TicketViews.TicketSummary;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
@PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
class TicketController {

	private final TicketService tickets;

	private final TicketRepository repository;

	private final StaffDirectory staff;

	TicketController(TicketService tickets, TicketRepository repository, StaffDirectory staff) {
		this.tickets = tickets;
		this.repository = repository;
		this.staff = staff;
	}

	@GetMapping
	@Transactional(readOnly = true)
	Page<TicketSummary> list(@RequestParam(required = false) TicketStatus status,
			@RequestParam(required = false) String category, @RequestParam(required = false) String assignee,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
			Authentication auth) {
		TicketQuery.requireSortable(pageable);
		TicketQuery query = new TicketQuery(status, category, assignee, q, from, to);
		Page<Ticket> page = repository.findAll(query.toSpecification(CurrentUser.from(auth)), pageable);
		Map<UUID, String> emails = staff.emails(page.getContent().stream().map(Ticket::getAssigneeId).toList());
		return page.map((ticket) -> TicketSummary.of(ticket, emails));
	}

	@GetMapping("/{id}")
	TicketDetail get(@PathVariable Long id, Authentication auth) {
		return tickets.detail(id, CurrentUser.from(auth));
	}

	@PostMapping("/{id}/claim")
	TicketSummary claim(@PathVariable Long id, Authentication auth) {
		return tickets.claim(id, CurrentUser.from(auth));
	}

	@PostMapping("/{id}/replies")
	@ResponseStatus(HttpStatus.CREATED)
	TicketDetail reply(@PathVariable Long id, @Valid @RequestBody ReplyRequest body, Authentication auth) {
		return tickets.reply(id, body.body(), CurrentUser.from(auth));
	}

	@PostMapping("/{id}/notes")
	@ResponseStatus(HttpStatus.CREATED)
	TicketDetail note(@PathVariable Long id, @Valid @RequestBody NoteRequest body, Authentication auth) {
		return tickets.addNote(id, body.body(), CurrentUser.from(auth));
	}

	@PatchMapping("/{id}")
	TicketDetail update(@PathVariable Long id, @RequestBody UpdateTicketRequest body, Authentication auth) {
		return tickets.update(id, body, CurrentUser.from(auth));
	}

	@PostMapping("/{id}/assign")
	@PreAuthorize("hasRole('ADMIN')")
	TicketSummary assign(@PathVariable Long id, @RequestBody AssignRequest body) {
		return tickets.assign(id, body.assigneeId());
	}

}
