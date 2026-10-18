package com.helpdesk.ticket;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One email in a ticket's thread, from the student (INBOUND) or to the student (OUTBOUND). */
@Entity
@Table(name = "ticket_messages")
public class TicketMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "ticket_id", nullable = false)
	private Long ticketId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MessageDirection direction;

	@Column(name = "author_user_id")
	private UUID authorUserId;

	@Column(nullable = false)
	private String body;

	@Column(name = "sent_by_ai", nullable = false)
	private boolean sentByAi;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected TicketMessage() {
	}

	private TicketMessage(Long ticketId, MessageDirection direction, UUID authorUserId, String body, boolean sentByAi,
			Instant createdAt) {
		this.ticketId = ticketId;
		this.direction = direction;
		this.authorUserId = authorUserId;
		this.body = body;
		this.sentByAi = sentByAi;
		this.createdAt = createdAt;
	}

	static TicketMessage fromStudent(Long ticketId, String body, Instant now) {
		return new TicketMessage(ticketId, MessageDirection.INBOUND, null, body, false, now);
	}

	/** A reply the AI sent on its own. */
	public static TicketMessage fromAi(Long ticketId, String body, Instant now) {
		return new TicketMessage(ticketId, MessageDirection.OUTBOUND, null, body, true, now);
	}

	static TicketMessage fromAgent(Long ticketId, UUID authorUserId, String body, Instant now) {
		return new TicketMessage(ticketId, MessageDirection.OUTBOUND, authorUserId, body, false, now);
	}

	public Long getId() {
		return id;
	}

	public Long getTicketId() {
		return ticketId;
	}

	public MessageDirection getDirection() {
		return direction;
	}

	public UUID getAuthorUserId() {
		return authorUserId;
	}

	public String getBody() {
		return body;
	}

	public boolean isSentByAi() {
		return sentByAi;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
