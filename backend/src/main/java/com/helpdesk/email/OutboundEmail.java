package com.helpdesk.email;

import java.io.Serializable;

/**
 * An email to a student. {@code ticketId} lets replies thread back via "[#id]" in the subject.
 * Serializable because it's stored as the data of the background send job.
 */
public record OutboundEmail(String to, String subject, String body, Long ticketId) implements Serializable {

	@Override
	public String toString() {
		// Bodies can contain personal details; keep them out of logs and error messages.
		return "OutboundEmail[to=" + to + ", ticketId=" + ticketId + "]";
	}

}
