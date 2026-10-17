package com.helpdesk.ticket;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A student's email, as a mail provider webhook would deliver it. {@code ticketRef} is optional;
 * a "[#123]" marker in the subject works too.
 */
public record InboundEmailRequest(@NotBlank @Email String fromEmail, @Size(max = 200) String fromName,
		@NotBlank @Size(max = TicketLimits.SUBJECT_MAX) String subject,
		@NotBlank @Size(max = TicketLimits.BODY_MAX) String body, Long ticketRef) {

	public InboundEmailRequest {
		fromEmail = fromEmail == null ? null : fromEmail.trim();
	}

}
