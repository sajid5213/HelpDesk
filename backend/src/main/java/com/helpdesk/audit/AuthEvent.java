package com.helpdesk.audit;

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

@Entity
@Table(name = "auth_events")
public class AuthEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false)
	private AuthEventType type;

	@Column(name = "user_id")
	private UUID userId;

	private String ip;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AuthEvent() {
	}

	AuthEvent(AuthEventType type, UUID userId, String ip, Instant createdAt) {
		this.type = type;
		this.userId = userId;
		this.ip = ip;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public AuthEventType getType() {
		return type;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getIp() {
		return ip;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
