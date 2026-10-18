package com.helpdesk.ai;

import java.util.List;

import com.helpdesk.kb.KbArticle;

/**
 * The AI behind the helpdesk. The real one calls Claude; tests use a scripted fake.
 *
 * @throws AiRefusedException when Claude declines the request (not retried; an agent takes over)
 * @throws AiUnavailableException for network, API, or malformed-output problems (retried)
 */
public interface AiClient {

	/** False when no API key is configured: tickets are then left to agents. */
	boolean isEnabled();

	Classification classify(TicketThread thread);

	ReplyDraft draftReply(TicketThread thread, List<KbArticle> knowledgeBase);

}
