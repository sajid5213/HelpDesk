package com.helpdesk.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Claude's structured reply draft, grounded in knowledge base articles. */
public record ReplyDraft(
		@JsonPropertyDescription("true only if the knowledge base articles fully answer the student's question") boolean answeredByKb,
		@JsonPropertyDescription("ids of the articles the reply is based on; empty if none apply") List<Long> articleIds,
		@JsonPropertyDescription("the email reply to the student: friendly, addressed by name, answering their actual "
				+ "question using only the articles. If the articles do not cover it, a short holding reply saying a "
				+ "team member will follow up") String reply) {

	public ReplyDraft {
		articleIds = articleIds == null ? List.of() : List.copyOf(articleIds);
	}

}
