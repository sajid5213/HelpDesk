package com.helpdesk.ticket;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

	/**
	 * Assigns the ticket only if nobody has it yet. Returns 0 when someone else won the race.
	 * Bumps the version so any copy loaded before the claim is now stale.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update Ticket t set t.assigneeId = :agentId, t.updatedAt = :now, t.version = t.version + 1
			where t.id = :id and t.assigneeId is null""")
	int claimIfUnassigned(@Param("id") Long id, @Param("agentId") UUID agentId, @Param("now") Instant now);

	/** Auto-close: RESOLVED tickets nobody has touched since {@code resolvedBefore}. */
	@Modifying
	@Query("""
			update Ticket t set t.status = com.helpdesk.ticket.TicketStatus.CLOSED, t.closedAt = :now,
				t.updatedAt = :now, t.version = t.version + 1
			where t.status = com.helpdesk.ticket.TicketStatus.RESOLVED and t.resolvedAt < :resolvedBefore""")
	int closeResolvedBefore(@Param("resolvedBefore") Instant resolvedBefore, @Param("now") Instant now);

}
