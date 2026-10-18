package com.helpdesk.email;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;

import com.helpdesk.common.HelpdeskMetrics;
import com.helpdesk.common.IntegrationTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Emails are background jobs stored with the change that caused them: sent once, never for a
 * rolled-back change, and retried while the mail server is down. (Test config: 3 attempts.)
 */
class EmailOutboxTest extends IntegrationTest {

	private static final OutboundEmail EMAIL = new OutboundEmail("sam.outbox@example.com", "Re: [#7] Video",
			"Clear your cache and reload.", 7L);

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MeterRegistry registry;

	@Test
	void committedEmailIsSentExactlyOnce() throws InterruptedException {
		requestInTransaction(false);

		eventually(() -> sentEmails.sent().size() == 1);
		Thread.sleep(1500); // a few more polls: no second send
		assertThat(sentEmails.sent()).containsExactly(EMAIL);
		assertThat(pendingJobs()).isZero();
	}

	@Test
	void rolledBackChangeSendsNothing() throws InterruptedException {
		requestInTransaction(true);

		Thread.sleep(1500);
		assertThat(sentEmails.attempts()).isZero();
		assertThat(pendingJobs()).isZero();
	}

	@Test
	void retriedWhileTheMailServerIsDown() {
		sentEmails.failNext(2);

		requestInTransaction(false);

		eventually(() -> sentEmails.sent().size() == 1);
		assertThat(sentEmails.attempts()).isEqualTo(3);
	}

	@Test
	void givesUpAfterEveryAttemptFailsAndCountsIt() {
		sentEmails.failNext(Integer.MAX_VALUE);
		double failedBefore = failed();

		requestInTransaction(false);

		eventually(() -> failed() == failedBefore + 1);
		assertThat(sentEmails.attempts()).isEqualTo(3);
		assertThat(sentEmails.sent()).isEmpty();
		assertThat(pendingJobs()).isZero();
	}

	private void requestInTransaction(boolean rollBack) {
		new TransactionTemplate(transactionManager).executeWithoutResult((status) -> {
			events.publishEvent(new OutboundEmailRequested(EMAIL));
			if (rollBack) {
				status.setRollbackOnly();
			}
		});
	}

	private int pendingJobs() {
		return jdbc.queryForObject("select count(*) from scheduled_tasks where task_name = ?", Integer.class,
				SendEmailTask.NAME);
	}

	private double failed() {
		var counter = registry.find(HelpdeskMetrics.EMAILS).tags("outcome", "failed").counter();
		return counter == null ? 0 : counter.count();
	}

}
