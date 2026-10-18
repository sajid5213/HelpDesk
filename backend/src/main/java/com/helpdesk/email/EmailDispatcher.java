package com.helpdesk.email;

import java.time.Instant;
import java.util.UUID;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns each requested email into a send job, stored in the same transaction as the change that
 * caused it (a transactional outbox). The scheduler's DataSource is transaction-aware, so the job
 * commits or rolls back together with the reply: no email for a rolled-back reply, and no lost
 * email for a saved one.
 */
@Component
class EmailDispatcher {

	private final Scheduler scheduler;

	private final OneTimeTask<OutboundEmail> task;

	EmailDispatcher(Scheduler scheduler, OneTimeTask<OutboundEmail> task) {
		this.scheduler = scheduler;
		this.task = task;
	}

	/** Due right away, on the scheduler's own (real) clock. */
	@EventListener
	void onOutboundEmail(OutboundEmailRequested event) {
		scheduler.schedule(task.instance("email-" + UUID.randomUUID(), event.email()), Instant.now());
	}

	/** The job became visible on commit: run it now instead of at the next poll. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	void sendNow(OutboundEmailRequested event) {
		scheduler.triggerCheckForDueExecutions();
	}

}
