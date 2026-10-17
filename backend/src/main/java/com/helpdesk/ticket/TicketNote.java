package com.helpdesk.ticket;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An internal note between staff. Never sent to the student. */
@Entity
@Table(name = "ticket_notes")
public class TicketNote {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "ticket_id", nullable = false)
	private Long ticketId;

	@Column(name = "author_user_id", nullable = false)
	private UUID authorUserId;

	@Column(nullable = false)
	private String body;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected TicketNote() {
	}

	TicketNote(Long ticketId, UUID authorUserId, String body, Instant createdAt) {
		this.ticketId = ticketId;
		this.authorUserId = authorUserId;
		this.body = body;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public Long getTicketId() {
		return ticketId;
	}

	public UUID getAuthorUserId() {
		return authorUserId;
	}

	public String getBody() {
		return body;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
