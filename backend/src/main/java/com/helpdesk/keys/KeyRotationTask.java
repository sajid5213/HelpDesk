package com.helpdesk.keys;

import java.time.Duration;
import java.time.Instant;

import com.github.kagkarlsson.scheduler.task.ExecutionComplete;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay;
import com.github.kagkarlsson.scheduler.task.schedule.Schedule;

import com.helpdesk.audit.AuditService;
import com.helpdesk.audit.AuthEventType;
import com.helpdesk.config.HelpdeskProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rotates the signing key on a fixed interval (default 7 days). Runs through db-scheduler, so
 * with several app instances exactly one rotates, and a restart does not reset the countdown.
 */
@Configuration(proxyBeanMethods = false)
public class KeyRotationTask {

	public static final String NAME = "key-rotation";

	@Bean
	RecurringTask<Void> keyRotationRecurringTask(SigningKeyService signingKeys, AuditService audit,
			HelpdeskProperties properties) {
		return Tasks.recurring(NAME, schedule(properties))
			.execute((instance, context) -> rotate(signingKeys, audit));
	}

	static FirstRunAfterInterval schedule(HelpdeskProperties properties) {
		return new FirstRunAfterInterval(properties.auth().keyRotationInterval());
	}

	static void rotate(SigningKeyService signingKeys, AuditService audit) {
		signingKeys.rotate();
		audit.record(AuthEventType.KEY_ROTATED, null, null);
	}

	/**
	 * Like {@link FixedDelay}, but the very first run is one interval after the scheduler first
	 * sees the task, not immediately (a fresh install already has a brand-new key).
	 */
	static final class FirstRunAfterInterval implements Schedule {

		private final Duration interval;

		private final FixedDelay delegate;

		FirstRunAfterInterval(Duration interval) {
			this.interval = interval;
			this.delegate = FixedDelay.of(interval);
		}

		Duration interval() {
			return interval;
		}

		@Override
		public Instant getInitialExecutionTime(Instant now) {
			return now.plus(interval);
		}

		@Override
		public Instant getNextExecutionTime(ExecutionComplete executionComplete) {
			return delegate.getNextExecutionTime(executionComplete);
		}

		@Override
		public boolean isDeterministic() {
			return delegate.isDeterministic();
		}

		@Override
		public String toString() {
			return "FirstRunAfterInterval(" + interval + ")";
		}

	}

}
