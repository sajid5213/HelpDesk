package com.helpdesk.kb;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KbArticleRepository extends JpaRepository<KbArticle, Long> {

	List<KbArticle> findAllByOrderByTitleAsc();

}
