package com.helpdesk.keys;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "signing_keys")
public class SigningKey {

	@Id
	private String kid;

	@Column(name = "public_jwk", nullable = false)
	private String publicJwk;

	@Column(name = "private_key_encrypted", nullable = false)
	private String privateKeyEncrypted;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private KeyStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "retired_at")
	private Instant retiredAt;

	protected SigningKey() {
	}

	SigningKey(String kid, String publicJwk, String privateKeyEncrypted, Instant createdAt) {
		this.kid = kid;
		this.publicJwk = publicJwk;
		this.privateKeyEncrypted = privateKeyEncrypted;
		this.status = KeyStatus.ACTIVE;
		this.createdAt = createdAt;
	}

	void retire(Instant now) {
		this.status = KeyStatus.RETIRED;
		this.retiredAt = now;
	}

	public String getKid() {
		return kid;
	}

	public String getPublicJwk() {
		return publicJwk;
	}

	String getPrivateKeyEncrypted() {
		return privateKeyEncrypted;
	}

	public KeyStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getRetiredAt() {
		return retiredAt;
	}

}
