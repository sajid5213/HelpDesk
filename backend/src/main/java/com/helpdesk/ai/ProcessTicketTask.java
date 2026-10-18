package com.helpdesk.ai;

import java.io.Serializable;

import com.github.kagkarlsson.scheduler.task.FailureHandler;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helpdesk.config.HelpdeskProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The background job that runs {@link TicketAiPipeline} for one student message. Stored in
 * PostgreSQL by db-scheduler, so it survives restarts, runs once across instances, and is retried
 * with exponential backoff.
 */
@Configuration(proxyBeanMethods = false)
public class ProcessTicketTask {

	public static final String NAME = "process-ticket";

	private static final Logger log = LoggerFactory.getLogger(ProcessTicketTask.class);

	@Bean
	OneTimeTask<Job> processTicketOneTimeTask(TicketAiPipeline pipeline, HelpdeskProperties properties) {
		HelpdeskProperties.Ai ai = properties.ai();
		int retries = Math.max(0, ai.maxAttempts() - 1);
		return Tasks.oneTime(NAME, Job.class)
			.onFailure(FailureHandler.<Job>maxRetries(retries).withBackoff(ai.retryDelay()).thenRemove((complete) -> {
				Job job = (Job) complete.getExecution().taskInstance.getData();
				log.warn("AI gave up on ticket {} after {} attempts; leaving it to agents", job.ticketId(),
						ai.maxAttempts());
				pipeline.markFailed(job.ticketId());
			}))
			.execute((instance, context) -> run(pipeline, instance.getData()));
	}

	static void run(TicketAiPipeline pipeline, Job job) {
		try {
			pipeline.process(job.ticketId(), job.newTicket());
		}
		catch (AiRefusedException ex) {
			// Not retried: asking again would be declined again. A person handles it.
			log.info("Claude declined ticket {}; leaving it to agents", job.ticketId());
			pipeline.markSkipped(job.ticketId());
		}
	}

	/** Stored with the scheduled job. */
	public record Job(Long ticketId, Long messageId, boolean newTicket) implements Serializable {
	}

}
