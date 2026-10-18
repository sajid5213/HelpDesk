package com.helpdesk.ticket;

/** Where the AI is with a ticket. FAILED and SKIPPED tickets are handled by an agent. */
public enum AiStatus {

	/** Waiting for the AI job. */
	PENDING,

	/** Classified and summarized; a reply was sent or suggested. */
	DONE,

	/** The AI kept failing after all retries. */
	FAILED,

	/** Not run: AI is turned off, or Claude declined the request. */
	SKIPPED

}
