package com.helpdesk.ticket;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request bodies for the ticket API. */
public final class TicketRequests {

	private TicketRequests() {
	}

	public record ReplyRequest(@NotBlank @Size(max = TicketLimits.BODY_MAX) String body) {
	}

	public record NoteRequest(@NotBlank @Size(max = TicketLimits.BODY_MAX) String body) {
	}

	/** Both optional. */
	public record UpdateTicketRequest(TicketStatus status, TicketCategory category) {
	}

	/** {@code assigneeId} null means unassign. */
	public record AssignRequest(UUID assigneeId) {
	}

}
