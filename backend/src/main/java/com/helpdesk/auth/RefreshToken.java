package com.helpdesk.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "token_hash", nullable = false, unique = true)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "replaced_by_id")
	private UUID replacedById;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected RefreshToken() {
	}

	RefreshToken(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	/** Revoked by logout, rotation, or reuse detection. */
	boolean isRevoked() {
		return revokedAt != null;
	}

	boolean isExpiredAt(Instant now) {
		return !expiresAt.isAfter(now);
	}

	void rotateTo(RefreshToken next, Instant now) {
		this.revokedAt = now;
		this.replacedById = next.id;
	}

	void revoke(Instant now) {
		if (revokedAt == null) {
			revokedAt = now;
		}
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public UUID getReplacedById() {
		return replacedById;
	}

}
