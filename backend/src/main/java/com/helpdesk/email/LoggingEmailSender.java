package com.helpdesk.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default when no mail server is set up: records that an email would be sent. Never logs the body. */
@Component
@ConditionalOnProperty(name = "helpdesk.email.mode", havingValue = "log", matchIfMissing = true)
class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	@Override
	public void send(OutboundEmail email) {
		log.info("Email to {} for ticket {} (not sent: helpdesk.email.mode=log)", email.to(), email.ticketId());
	}

}
