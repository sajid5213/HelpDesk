package com.helpdesk.ticket;

import com.helpdesk.common.CurrentUser;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * The one place that decides who may see or change a ticket.
 * <ul>
 * <li>ADMIN: every ticket.</li>
 * <li>AGENT: reads unassigned tickets and their own; changes only their own.</li>
 * </ul>
 * Another agent's ticket is a 403, for reading and for changing.
 */
@Component
public class TicketAccessPolicy {

	public boolean canRead(CurrentUser user, Ticket ticket) {
		return user.admin() || ticket.isUnassigned() || ticket.isAssignedTo(user.id());
	}

	public boolean canUpdate(CurrentUser user, Ticket ticket) {
		return user.admin() || ticket.isAssignedTo(user.id());
	}

	public void requireRead(CurrentUser user, Ticket ticket) {
		if (!canRead(user, ticket)) {
			throw new AccessDeniedException("Ticket " + ticket.getId() + " is assigned to another agent");
		}
	}

	public void requireUpdate(CurrentUser user, Ticket ticket) {
		if (!canUpdate(user, ticket)) {
			throw new AccessDeniedException("Ticket " + ticket.getId() + " must be assigned to you first");
		}
	}

}
