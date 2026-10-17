package com.helpdesk.ticket;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Numbers for the admin dashboard. */
@Service
public class StatsService {

	private final EntityManager em;

	StatsService(EntityManager em) {
		this.em = em;
	}

	@Transactional(readOnly = true)
	public Stats current() {
		Map<TicketStatus, Long> byStatus = new EnumMap<>(TicketStatus.class);
		for (TicketStatus status : TicketStatus.values()) {
			byStatus.put(status, 0L);
		}
		em.createQuery("select t.status, count(t) from Ticket t group by t.status", Object[].class)
			.getResultList()
			.forEach((row) -> byStatus.put((TicketStatus) row[0], (Long) row[1]));

		Map<String, Long> byCategory = new LinkedHashMap<>();
		for (TicketCategory category : TicketCategory.values()) {
			byCategory.put(category.name(), 0L);
		}
		byCategory.put(TicketQuery.UNCLASSIFIED, 0L);
		em.createQuery("select t.category, count(t) from Ticket t group by t.category", Object[].class)
			.getResultList()
			.forEach((row) -> byCategory.put(row[0] == null ? TicketQuery.UNCLASSIFIED : ((TicketCategory) row[0]).name(),
					(Long) row[1]));

		long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
		long autoResolved = count("select count(t) from Ticket t where t.autoResolved = true");
		long agentAnswered = count(
				"select count(t) from Ticket t where t.firstResponseAt is not null and t.autoResolved = false");
		Number avgSeconds = (Number) em.createNativeQuery("""
				select avg(extract(epoch from (first_response_at - created_at)))
				from tickets where first_response_at is not null""").getSingleResult();
		Double avgFirstResponseMinutes = avgSeconds == null ? null
				: Math.round(avgSeconds.doubleValue() / 60.0 * 10.0) / 10.0;

		return new Stats(total, byStatus, byCategory, autoResolved, agentAnswered, avgFirstResponseMinutes);
	}

	private long count(String jpql) {
		return em.createQuery(jpql, Long.class).getSingleResult();
	}

	/**
	 * {@code agentAnswered}: tickets a person replied to. {@code avgFirstResponseMinutes}: average
	 * time from the student's first email to the first reply, one decimal; null when nothing has
	 * been answered yet.
	 */
	public record Stats(long total, Map<TicketStatus, Long> byStatus, Map<String, Long> byCategory, long autoResolved,
			long agentAnswered, Double avgFirstResponseMinutes) {
	}

}
