package com.helpdesk.ticket;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulated inbound email, for development and demos. A real mail provider webhook (Mailgun,
 * Phase 9) will call {@link InboundEmailService} the same way, authenticated by its signature.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
class InboundEmailController {

	private final InboundEmailService inbound;

	InboundEmailController(InboundEmailService inbound) {
		this.inbound = inbound;
	}

	@PostMapping("/api/inbound/emails")
	ResponseEntity<InboundEmailService.Result> receive(@Valid @RequestBody InboundEmailRequest email) {
		InboundEmailService.Result result = inbound.receive(email);
		return ResponseEntity.created(URI.create("/api/tickets/" + result.ticketId())).body(result);
	}

}
