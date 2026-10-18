package com.helpdesk.email;

/**
 * Sends email to students: {@link LoggingEmailSender} (log only) or {@link SmtpEmailSender}, chosen
 * by {@code helpdesk.email.mode}. Called by the send job, never directly by business code.
 */
public interface EmailSender {

	void send(OutboundEmail email);

}
