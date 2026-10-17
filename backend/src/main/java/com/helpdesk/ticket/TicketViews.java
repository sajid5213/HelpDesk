package com.helpdesk.ticket;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response shapes for the ticket API. */
public final class TicketViews {

	private TicketViews() {
	}

	public record TicketSummary(Long id, String subject, String studentEmail, String studentName, TicketStatus status,
			TicketCategory category, UUID assigneeId, Instant createdAt, Instant updatedAt) {

		static TicketSummary of(Ticket t) {
			return new TicketSummary(t.getId(), t.getSubject(), t.getStudentEmail(), t.getStudentName(), t.getStatus(),
					t.getCategory(), t.getAssigneeId(), t.getCreatedAt(), t.getUpdatedAt());
		}

	}

	public record TicketDetail(Long id, String subject, String studentEmail, String studentName, TicketStatus status,
			TicketCategory category, boolean categorySetManually, UUID assigneeId, String aiSummary,
			String suggestedReply, boolean autoResolved, Instant createdAt, Instant updatedAt, Instant firstResponseAt,
			Instant resolvedAt, Instant closedAt, List<MessageView> messages, List<NoteView> notes) {

		static TicketDetail of(Ticket t, List<TicketMessage> messages, List<TicketNote> notes) {
			return new TicketDetail(t.getId(), t.getSubject(), t.getStudentEmail(), t.getStudentName(), t.getStatus(),
					t.getCategory(), t.isCategorySetManually(), t.getAssigneeId(), t.getAiSummary(),
					t.getSuggestedReply(), t.isAutoResolved(), t.getCreatedAt(), t.getUpdatedAt(),
					t.getFirstResponseAt(), t.getResolvedAt(), t.getClosedAt(),
					messages.stream().map(MessageView::of).toList(), notes.stream().map(NoteView::of).toList());
		}

	}

	public record MessageView(Long id, MessageDirection direction, UUID authorUserId, String body, boolean sentByAi,
			Instant createdAt) {

		static MessageView of(TicketMessage m) {
			return new MessageView(m.getId(), m.getDirection(), m.getAuthorUserId(), m.getBody(), m.isSentByAi(),
					m.getCreatedAt());
		}

	}

	public record NoteView(Long id, UUID authorUserId, String body, Instant createdAt) {

		static NoteView of(TicketNote n) {
			return new NoteView(n.getId(), n.getAuthorUserId(), n.getBody(), n.getCreatedAt());
		}

	}

}
