package com.helpdesk.email;

/** An email to a student. {@code ticketId} lets replies thread back via "[#id]" in the subject. */
public record OutboundEmail(String to, String subject, String body, Long ticketId) {

	@Override
	public String toString() {
		// Bodies can contain personal details; keep them out of logs and error messages.
		return "OutboundEmail[to=" + to + ", ticketId=" + ticketId + "]";
	}

}
