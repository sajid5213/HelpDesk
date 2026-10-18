package com.helpdesk.email;

import com.github.kagkarlsson.scheduler.task.FailureHandler;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helpdesk.common.HelpdeskMetrics;
import com.helpdesk.config.HelpdeskProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The background job that sends one email. Stored in PostgreSQL with the reply that caused it, so
 * an email is never lost when the mail server is down: it's retried with exponential backoff.
 */
@Configuration(proxyBeanMethods = false)
class SendEmailTask {

	static final String NAME = "send-email";

	private static final Logger log = LoggerFactory.getLogger(SendEmailTask.class);

	@Bean
	OneTimeTask<OutboundEmail> sendEmailOneTimeTask(EmailSender sender, HelpdeskMetrics metrics,
			HelpdeskProperties properties) {
		HelpdeskProperties.Email email = properties.email();
		return Tasks.oneTime(NAME, OutboundEmail.class)
			.onFailure(FailureHandler.<OutboundEmail>maxRetries(email.maxAttempts() - 1)
				.withBackoff(email.retryDelay())
				.thenRemove((complete) -> {
					OutboundEmail failed = (OutboundEmail) complete.getExecution().taskInstance.getData();
					log.warn("Gave up sending {} after {} attempts", failed, email.maxAttempts());
					metrics.emailSent(false);
				}))
			.execute((instance, context) -> {
				sender.send(instance.getData());
				metrics.emailSent(true);
			});
	}

}
