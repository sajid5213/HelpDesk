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
import jakarta.persistence.Version;

import com.helpdesk.common.Emails;

@Entity
@Table(name = "tickets")
public class Ticket {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String subject;

	@Column(name = "student_email", nullable = false)
	private String studentEmail;

	@Column(name = "student_name")
	private String studentName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TicketStatus status;

	@Enumerated(EnumType.STRING)
	private TicketCategory category;

	@Column(name = "category_set_manually", nullable = false)
	private boolean categorySetManually;

	@Column(name = "ai_summary")
	private String aiSummary;

	@Column(name = "suggested_reply")
	private String suggestedReply;

	@Column(name = "auto_resolved", nullable = false)
	private boolean autoResolved;

	@Column(name = "assignee_id")
	private UUID assigneeId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "first_response_at")
	private Instant firstResponseAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	/** Optimistic locking: a write based on a stale copy fails instead of silently overwriting. */
	@Version
	private int version;

	protected Ticket() {
	}

	public Ticket(String subject, String studentEmail, String studentName, Instant now) {
		this.subject = subject;
		this.studentEmail = Emails.normalize(studentEmail);
		this.studentName = studentName;
		this.status = TicketStatus.OPEN;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public boolean isFrom(String email) {
		return studentEmail.equals(Emails.normalize(email));
	}

	/** A new message from the student. A resolved ticket reopens; the assignee is kept. */
	void receivedMessage(Instant now) {
		if (status == TicketStatus.RESOLVED) {
			status = TicketStatus.OPEN;
			resolvedAt = null;
		}
		updatedAt = now;
	}

	/** Someone answered the student. */
	void replied(Instant now) {
		if (firstResponseAt == null) {
			firstResponseAt = now;
		}
		resolve(now);
	}

	void resolve(Instant now) {
		status = TicketStatus.RESOLVED;
		resolvedAt = now;
		updatedAt = now;
	}

	void reopen(Instant now) {
		status = TicketStatus.OPEN;
		resolvedAt = null;
		updatedAt = now;
	}

	void close(Instant now) {
		status = TicketStatus.CLOSED;
		closedAt = now;
		updatedAt = now;
	}

	void changeCategory(TicketCategory category, Instant now) {
		this.category = category;
		this.categorySetManually = true;
		this.updatedAt = now;
	}

	void assignTo(UUID assigneeId, Instant now) {
		this.assigneeId = assigneeId;
		this.updatedAt = now;
	}

	void touch(Instant now) {
		this.updatedAt = now;
	}

	public boolean isAssignedTo(UUID userId) {
		return assigneeId != null && assigneeId.equals(userId);
	}

	public boolean isUnassigned() {
		return assigneeId == null;
	}

	public Long getId() {
		return id;
	}

	public String getSubject() {
		return subject;
	}

	public String getStudentEmail() {
		return studentEmail;
	}

	public String getStudentName() {
		return studentName;
	}

	public TicketStatus getStatus() {
		return status;
	}

	public TicketCategory getCategory() {
		return category;
	}

	public boolean isCategorySetManually() {
		return categorySetManually;
	}

	public String getAiSummary() {
		return aiSummary;
	}

	public String getSuggestedReply() {
		return suggestedReply;
	}

	public boolean isAutoResolved() {
		return autoResolved;
	}

	public UUID getAssigneeId() {
		return assigneeId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getFirstResponseAt() {
		return firstResponseAt;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	public int getVersion() {
		return version;
	}

}
