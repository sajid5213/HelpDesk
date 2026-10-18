package com.helpdesk.ai;

/** The AI could not produce a usable answer this time (network, API error, bad output). Retried. */
public class AiUnavailableException extends RuntimeException {

	public AiUnavailableException(String message) {
		super(message);
	}

	public AiUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
