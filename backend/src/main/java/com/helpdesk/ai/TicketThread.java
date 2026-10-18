package com.helpdesk.ai;

import java.util.List;

/** What the AI sees of a ticket: the subject, the student's name, and the email thread in order. */
public record TicketThread(Long ticketId, String subject, String studentName, List<Message> messages) {

	/** {@code fromStudent} false means it was a reply from us (an agent or the AI). */
	public record Message(boolean fromStudent, String body) {
	}

}
