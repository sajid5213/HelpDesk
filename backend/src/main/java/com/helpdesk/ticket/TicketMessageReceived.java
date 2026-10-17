package com.helpdesk.ticket;

/**
 * Published after a student's email is stored. Phase 5 listens to it to classify, summarize,
 * and draft a reply.
 */
public record TicketMessageReceived(Long ticketId, Long messageId, boolean newTicket) {
}
