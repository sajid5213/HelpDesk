package com.helpdesk.ticket;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helpdesk.config.HelpdeskProperties;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Closes RESOLVED tickets the student has not replied to for a while (default 7 days). */
@Service
public class AutoCloseService {

	private static final Logger log = LoggerFactory.getLogger(AutoCloseService.class);

	private final TicketRepository tickets;

	private final Clock clock;

	private final Duration closeAfter;

	AutoCloseService(TicketRepository tickets, Clock clock, HelpdeskProperties properties) {
		this.tickets = tickets;
		this.clock = clock;
		this.closeAfter = properties.tickets().autoCloseAfter();
	}

	/** Returns how many tickets were closed. */
	@Transactional
	public int closeStaleResolvedTickets() {
		Instant now = clock.instant();
		int closed = tickets.closeResolvedBefore(now.minus(closeAfter), now);
		if (closed > 0) {
			log.info("Auto-closed {} resolved tickets", closed);
		}
		return closed;
	}

}
