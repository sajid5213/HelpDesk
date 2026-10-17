package com.helpdesk.audit;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

	private final AuthEventRepository events;

	private final Clock clock;

	AuditService(AuthEventRepository events, Clock clock) {
		this.events = events;
		this.clock = clock;
	}

	/**
	 * Records an auth event in its own transaction, so the row survives even when the
	 * calling transaction rolls back (for example a failed login).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(AuthEventType type, UUID userIdOrNull, String ipOrNull) {
		events.save(new AuthEvent(type, userIdOrNull, ipOrNull, clock.instant()));
	}

}
