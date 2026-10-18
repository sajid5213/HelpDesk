package com.helpdesk.audit;

import java.time.Clock;
import java.util.Arrays;
import java.util.UUID;

import com.helpdesk.common.HelpdeskMetrics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

	private final AuthEventRepository events;

	private final Clock clock;

	private final HelpdeskMetrics metrics;

	AuditService(AuthEventRepository events, Clock clock, HelpdeskMetrics metrics) {
		this.events = events;
		this.clock = clock;
		this.metrics = metrics;
		metrics.registerAuthEventTypes(Arrays.stream(AuthEventType.values()).map(Enum::name).toList());
	}

	/**
	 * Records an auth event in its own transaction, so the row survives even when the
	 * calling transaction rolls back (for example a failed login).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(AuthEventType type, UUID userIdOrNull, String ipOrNull) {
		record(type, userIdOrNull, null, ipOrNull);
	}

	/**
	 * Records an action an admin ({@code actorId}) took on an account ({@code userId}). Joins the
	 * caller's transaction, so the change and its audit row commit or roll back together.
	 */
	@Transactional
	public void record(AuthEventType type, UUID userIdOrNull, UUID actorIdOrNull, String ipOrNull) {
		events.save(new AuthEvent(type, userIdOrNull, actorIdOrNull, ipOrNull, clock.instant()));
		metrics.authEvent(type.name());
	}

}
