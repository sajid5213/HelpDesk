package com.helpdesk.ai;

import java.util.List;

import com.helpdesk.kb.KbArticle;

/** Used when no API key is set. Never called by the pipeline, which checks {@link #isEnabled()}. */
class DisabledAiClient implements AiClient {

	@Override
	public boolean isEnabled() {
		return false;
	}

	@Override
	public Classification classify(TicketThread thread) {
		throw new AiUnavailableException("AI is turned off (no ANTHROPIC_API_KEY)");
	}

	@Override
	public ReplyDraft draftReply(TicketThread thread, List<KbArticle> knowledgeBase) {
		throw new AiUnavailableException("AI is turned off (no ANTHROPIC_API_KEY)");
	}

}
