package com.helpdesk.ticket;

/** Size limits for user-supplied ticket text. */
final class TicketLimits {

	static final int SUBJECT_MAX = 300;

	static final int BODY_MAX = 50_000;

	private TicketLimits() {
	}

}
