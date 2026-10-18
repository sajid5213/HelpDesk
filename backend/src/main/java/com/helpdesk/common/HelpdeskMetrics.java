package com.helpdesk.common;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

/**
 * The app's own counters, exported to Prometheus. Tags are a few fixed values only: never a user
 * id, email address, ticket id or token, so metrics hold no personal data and stay cheap.
 */
@Component
public class HelpdeskMetrics {

	public static final String AUTH_EVENTS = "helpdesk.auth.events";

	public static final String AUTH_LOCKOUTS = "helpdesk.auth.lockouts";

	public static final String AUTH_RATE_LIMITED = "helpdesk.auth.rate_limited";

	public static final String TICKETS_OPENED = "helpdesk.tickets.opened";

	public static final String AI_OUTCOMES = "helpdesk.ai.outcomes";

	public static final String EMAILS = "helpdesk.emails";

	/** How the AI pipeline ended for one student message. */
	public enum AiOutcome {

		AUTO_RESOLVED, SUGGESTED, FAILED, SKIPPED;

		String tag() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	private final MeterRegistry registry;

	public HelpdeskMetrics(MeterRegistry registry) {
		this.registry = registry;
		// Registered up front, so dashboards show 0 instead of "no data" before the first event.
		registry.counter(AUTH_LOCKOUTS);
		registry.counter(TICKETS_OPENED);
		for (String scope : List.of("ip", "account")) {
			registry.counter(AUTH_RATE_LIMITED, "scope", scope);
		}
		for (AiOutcome outcome : AiOutcome.values()) {
			registry.counter(AI_OUTCOMES, "outcome", outcome.tag());
		}
		for (String outcome : List.of("sent", "failed")) {
			registry.counter(EMAILS, "outcome", outcome);
		}
	}

	/** Registers every auth event type at 0, so each shows on dashboards before it first happens. */
	public void registerAuthEventTypes(Collection<String> types) {
		types.forEach((type) -> registry.counter(AUTH_EVENTS, "type", type));
	}

	/** One auth audit event, by type (LOGIN_SUCCESS, TOKEN_REUSE_DETECTED, ...). */
	public void authEvent(String type) {
		registry.counter(AUTH_EVENTS, "type", type).increment();
	}

	public void lockout() {
		registry.counter(AUTH_LOCKOUTS).increment();
	}

	/** A login rejected by the rate limiter; {@code scope} is "ip" or "account". */
	public void rateLimited(String scope) {
		registry.counter(AUTH_RATE_LIMITED, "scope", scope).increment();
	}

	public void ticketOpened() {
		registry.counter(TICKETS_OPENED).increment();
	}

	/** One email job finished: sent, or given up after every retry. */
	public void emailSent(boolean sent) {
		registry.counter(EMAILS, "outcome", sent ? "sent" : "failed").increment();
	}

	public void aiOutcome(AiOutcome outcome) {
		registry.counter(AI_OUTCOMES, "outcome", outcome.tag()).increment();
	}

}
