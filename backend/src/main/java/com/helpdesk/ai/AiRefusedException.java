package com.helpdesk.ai;

/** Claude declined the request (stop reason "refusal"). Not retried: the ticket goes to an agent. */
public class AiRefusedException extends RuntimeException {

	public AiRefusedException(String message) {
		super(message);
	}

}
