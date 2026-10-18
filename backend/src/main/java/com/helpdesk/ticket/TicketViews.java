package com.helpdesk.ticket;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Response shapes for the ticket API. */
public final class TicketViews {

	private TicketViews() {
	}

	public record TicketSummary(Long id, String subject, String studentEmail, String studentName, TicketStatus status,
			TicketCategory category, AiStatus aiStatus, UUID assigneeId, String assigneeEmail, Instant createdAt,
			Instant updatedAt) {

		static TicketSummary of(Ticket t, Map<UUID, String> staffEmails) {
			return new TicketSummary(t.getId(), t.getSubject(), t.getStudentEmail(), t.getStudentName(), t.getStatus(),
					t.getCategory(), t.getAiStatus(), t.getAssigneeId(), staffEmails.get(t.getAssigneeId()),
					t.getCreatedAt(), t.getUpdatedAt());
		}

	}

	public record TicketDetail(Long id, String subject, String studentEmail, String studentName, TicketStatus status,
			TicketCategory category, boolean categorySetManually, UUID assigneeId, String assigneeEmail, AiStatus aiStatus,
			String aiSummary,
			String suggestedReply, boolean autoResolved, Instant createdAt, Instant updatedAt, Instant firstResponseAt,
			Instant resolvedAt, Instant closedAt, List<MessageView> messages, List<NoteView> notes) {

		static TicketDetail of(Ticket t, List<TicketMessage> messages, List<TicketNote> notes,
				Map<UUID, String> staffEmails) {
			return new TicketDetail(t.getId(), t.getSubject(), t.getStudentEmail(), t.getStudentName(), t.getStatus(),
					t.getCategory(), t.isCategorySetManually(), t.getAssigneeId(), staffEmails.get(t.getAssigneeId()),
					t.getAiStatus(), t.getAiSummary(),
					t.getSuggestedReply(), t.isAutoResolved(), t.getCreatedAt(), t.getUpdatedAt(),
					t.getFirstResponseAt(), t.getResolvedAt(), t.getClosedAt(),
					messages.stream().map((m) -> MessageView.of(m, staffEmails)).toList(),
					notes.stream().map((n) -> NoteView.of(n, staffEmails)).toList());
		}

	}

	public record MessageView(Long id, MessageDirection direction, UUID authorUserId, String authorEmail, String body,
			boolean sentByAi, Instant createdAt) {

		static MessageView of(TicketMessage m, Map<UUID, String> staffEmails) {
			return new MessageView(m.getId(), m.getDirection(), m.getAuthorUserId(),
					staffEmails.get(m.getAuthorUserId()), m.getBody(), m.isSentByAi(), m.getCreatedAt());
		}

	}

	public record NoteView(Long id, UUID authorUserId, String authorEmail, String body, Instant createdAt) {

		static NoteView of(TicketNote n, Map<UUID, String> staffEmails) {
			return new NoteView(n.getId(), n.getAuthorUserId(), staffEmails.get(n.getAuthorUserId()), n.getBody(),
					n.getCreatedAt());
		}

	}

}
