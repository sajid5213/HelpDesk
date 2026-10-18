package com.helpdesk.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import com.helpdesk.ticket.TicketCategory;

/**
 * Claude's structured answer for "what is this ticket about?". The field descriptions become the
 * JSON schema Claude must follow.
 */
public record Classification(
		@JsonPropertyDescription("GENERAL_QUESTION for course content, access, certificates, accounts and other "
				+ "questions; TECHNICAL_QUESTION for something not working (videos, code, website, errors); "
				+ "REFUND_REQUEST when the student asks for money back") TicketCategory category,
		@JsonPropertyDescription("One or two sentences for a support agent: what the student needs and anything "
				+ "important they mentioned") String summary,
		@JsonPropertyDescription("true if the student asks for a refund, money back, a chargeback, or to cancel and "
				+ "be repaid, in any wording, even if it is not the main topic") boolean mentionsRefund) {
}
