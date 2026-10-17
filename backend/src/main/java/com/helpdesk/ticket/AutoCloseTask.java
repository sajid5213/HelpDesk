package com.helpdesk.ticket;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Runs auto-close every hour through db-scheduler (once across all app instances). */
@Configuration(proxyBeanMethods = false)
public class AutoCloseTask {

	public static final String NAME = "ticket-auto-close";

	@Bean
	RecurringTask<Void> ticketAutoCloseRecurringTask(AutoCloseService autoClose) {
		return Tasks.recurring(NAME, FixedDelay.ofHours(1))
			.execute((instance, context) -> autoClose.closeStaleResolvedTickets());
	}

}
