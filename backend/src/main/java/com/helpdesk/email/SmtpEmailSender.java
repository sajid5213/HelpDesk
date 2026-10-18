package com.helpdesk.email;

import com.helpdesk.config.HelpdeskProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends email over SMTP (Spring Mail). Locally the server is Mailpit, which catches every email.
 * A failure throws, so the send job retries it.
 */
@Component
@ConditionalOnProperty(name = "helpdesk.email.mode", havingValue = HelpdeskProperties.Email.SMTP)
class SmtpEmailSender implements EmailSender {

	private final JavaMailSender mail;

	private final String from;

	SmtpEmailSender(JavaMailSender mail, HelpdeskProperties properties) {
		this.mail = mail;
		this.from = properties.email().from();
	}

	@Override
	public void send(OutboundEmail email) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email.to());
		message.setSubject(email.subject());
		message.setText(email.body());
		mail.send(message);
	}

}
