package com.helpdesk.email;

/**
 * Sends email to students. Swappable: {@link LoggingEmailSender} now, SMTP (Spring Mail) in
 * Phase 9, without changing callers.
 */
public interface EmailSender {

	void send(OutboundEmail email);

}
