package com.helpdesk.kb;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.helpdesk.common.NotFoundException;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Knowledge base: everyone on staff reads, only admins write. */
@RestController
@RequestMapping("/api/kb/articles")
@PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
class KbArticleController {

	private final KbArticleRepository articles;

	private final Clock clock;

	KbArticleController(KbArticleRepository articles, Clock clock) {
		this.articles = articles;
		this.clock = clock;
	}

	@GetMapping
	List<ArticleView> list() {
		return articles.findAllByOrderByTitleAsc().stream().map(ArticleView::of).toList();
	}

	@GetMapping("/{id}")
	ArticleView get(@PathVariable Long id) {
		return ArticleView.of(find(id));
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<ArticleView> create(@Valid @RequestBody ArticleRequest body) {
		KbArticle saved = articles.save(new KbArticle(body.title().trim(), body.body(), clock.instant()));
		return ResponseEntity.created(URI.create("/api/kb/articles/" + saved.getId())).body(ArticleView.of(saved));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	ArticleView update(@PathVariable Long id, @Valid @RequestBody ArticleRequest body) {
		KbArticle article = find(id);
		article.update(body.title().trim(), body.body(), clock.instant());
		return ArticleView.of(articles.save(article));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<Void> delete(@PathVariable Long id) {
		articles.delete(find(id));
		return ResponseEntity.noContent().build();
	}

	private KbArticle find(Long id) {
		return articles.findById(id).orElseThrow(() -> new NotFoundException("Article " + id + " not found"));
	}

	record ArticleRequest(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 50_000) String body) {
	}

	record ArticleView(Long id, String title, String body, Instant createdAt, Instant updatedAt) {

		static ArticleView of(KbArticle a) {
			return new ArticleView(a.getId(), a.getTitle(), a.getBody(), a.getCreatedAt(), a.getUpdatedAt());
		}

	}

}
