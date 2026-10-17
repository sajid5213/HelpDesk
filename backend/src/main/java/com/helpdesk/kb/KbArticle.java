package com.helpdesk.kb;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A knowledge-base article: an FAQ answer or a policy such as the refund rules. */
@Entity
@Table(name = "kb_articles")
public class KbArticle {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String body;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected KbArticle() {
	}

	public KbArticle(String title, String body, Instant now) {
		this.title = title;
		this.body = body;
		this.createdAt = now;
		this.updatedAt = now;
	}

	void update(String title, String body, Instant now) {
		this.title = title;
		this.body = body;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getBody() {
		return body;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
