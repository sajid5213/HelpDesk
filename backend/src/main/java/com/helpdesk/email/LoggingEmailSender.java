package com.helpdesk.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/** Stand-in until real email exists: records that an email would be sent. Never logs the body. */
@Component
class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	@Override
	public void send(OutboundEmail email) {
		log.info("Email to {} for ticket {} (not sent: no email provider configured)", email.to(), email.ticketId());
	}

}
