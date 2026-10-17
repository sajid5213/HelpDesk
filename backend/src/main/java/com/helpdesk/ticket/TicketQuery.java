package com.helpdesk.ticket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.persistence.criteria.Predicate;

import com.helpdesk.common.ApiException;
import com.helpdesk.common.CurrentUser;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

/**
 * Filters for the ticket list, turned into a JPA Specification. Agents are always limited to
 * their own and unassigned tickets, whatever they ask for.
 */
public record TicketQuery(TicketStatus status, String category, String assignee, String q, Instant from,
		Instant to) {

	static final String UNCLASSIFIED = "UNCLASSIFIED";

	static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt");

	public enum AssigneeFilter {

		MINE, UNASSIGNED, ANY

	}

	Specification<Ticket> toSpecification(CurrentUser user) {
		return (root, query, cb) -> {
			List<Predicate> where = new ArrayList<>();
			if (status != null) {
				where.add(cb.equal(root.get("status"), status));
			}
			if (category != null && !category.isBlank()) {
				where.add(UNCLASSIFIED.equalsIgnoreCase(category) ? cb.isNull(root.get("category"))
						: cb.equal(root.get("category"), parseCategory(category)));
			}
			AssigneeFilter scope = parseAssignee(assignee);
			switch (scope) {
				case MINE -> where.add(cb.equal(root.get("assigneeId"), user.id()));
				case UNASSIGNED -> where.add(cb.isNull(root.get("assigneeId")));
				case ANY -> {
					if (!user.admin()) {
						where.add(cb.or(cb.isNull(root.get("assigneeId")), cb.equal(root.get("assigneeId"), user.id())));
					}
				}
			}
			if (q != null && !q.isBlank()) {
				String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
				where.add(cb.or(cb.like(cb.lower(root.get("subject")), pattern, '\\'),
						cb.like(root.get("studentEmail"), pattern, '\\')));
			}
			if (from != null) {
				where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
			}
			if (to != null) {
				where.add(cb.lessThan(root.get("createdAt"), to));
			}
			return cb.and(where.toArray(Predicate[]::new));
		};
	}

	/** Only known fields may be sorted on; anything else is a 400 instead of a database error. */
	static void requireSortable(Pageable pageable) {
		for (Sort.Order order : pageable.getSort()) {
			if (!SORTABLE.contains(order.getProperty())) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_sort",
						"Sort by one of " + SORTABLE + ", not '" + order.getProperty() + "'");
			}
		}
	}

	/** mine, unassigned, or any (the default), in any letter case. */
	private static AssigneeFilter parseAssignee(String assignee) {
		if (assignee == null || assignee.isBlank()) {
			return AssigneeFilter.ANY;
		}
		try {
			return AssigneeFilter.valueOf(assignee.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_assignee_filter",
					"assignee must be mine, unassigned, or any");
		}
	}

	private static TicketCategory parseCategory(String category) {
		try {
			return TicketCategory.valueOf(category.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_category", "Unknown category '" + category + "'");
		}
	}

	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
