package com.helpdesk.ticket;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The dashboard, as an API for now (no screen yet). */
@RestController
@PreAuthorize("hasRole('ADMIN')")
class StatsController {

	private final StatsService stats;

	StatsController(StatsService stats) {
		this.stats = stats;
	}

	@GetMapping("/api/stats")
	StatsService.Stats stats() {
		return stats.current();
	}

}
