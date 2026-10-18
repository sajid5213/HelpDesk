package com.helpdesk.ai;

import java.time.Clock;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;

import com.helpdesk.ticket.TicketMessageReceived;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Hands each stored student message to the AI job, only after the email is safely committed.
 * One job per message: a repeated event cannot run the AI twice.
 */
@Component
class TicketAiScheduler {

	private final AiClient ai;

	private final SchedulerClient scheduler;

	private final OneTimeTask<ProcessTicketTask.Job> task;

	private final TicketAiPipeline pipeline;

	private final Clock clock;

	TicketAiScheduler(AiClient ai, SchedulerClient scheduler, OneTimeTask<ProcessTicketTask.Job> task,
			TicketAiPipeline pipeline, Clock clock) {
		this.ai = ai;
		this.scheduler = scheduler;
		this.task = task;
		this.pipeline = pipeline;
		this.clock = clock;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onMessageReceived(TicketMessageReceived event) {
		if (!ai.isEnabled()) {
			pipeline.markSkipped(event.ticketId());
			return;
		}
		String instanceId = "ticket-" + event.ticketId() + "-msg-" + event.messageId();
		scheduler.scheduleIfNotExists(
				task.instance(instanceId, new ProcessTicketTask.Job(event.ticketId(), event.messageId(), event.newTicket())),
				clock.instant());
	}

}
