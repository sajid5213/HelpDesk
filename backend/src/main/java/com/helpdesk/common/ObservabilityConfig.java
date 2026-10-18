package com.helpdesk.common;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
class ObservabilityConfig {

	/** Background jobs that are due but not picked up yet: a growing number means the queue is stuck. */
	@Bean
	MeterBinder dueJobsGauge(JdbcTemplate jdbc) {
		return (registry) -> Gauge
			.builder("helpdesk.jobs.due", jdbc,
					(template) -> template.queryForObject(
							"select count(*) from scheduled_tasks where picked = false and execution_time <= now()",
							Long.class))
			.description("Scheduled jobs that are due and waiting to run")
			.register(registry);
	}

	/**
	 * SQL and security-filter spans only inside a trace that already exists (a request, an AI run).
	 * Without this, the job scheduler's polling and every Prometheus scrape would fill Tempo with
	 * one-span traces. A parent that was itself filtered out is a no-op and doesn't count.
	 */
	@Bean
	ObservationPredicate noOrphanBackgroundSpans() {
		return (name, context) -> !(name.startsWith("jdbc.") || name.startsWith("spring.security."))
				|| (context.getParentObservation() instanceof Observation parent && !parent.isNoop());
	}

}
