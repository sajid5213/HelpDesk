package com.helpdesk.email;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends an email only once the change that caused it is safely in the database. */
@Component
class EmailDispatcher {

	private final EmailSender sender;

	EmailDispatcher(EmailSender sender) {
		this.sender = sender;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onOutboundEmail(OutboundEmailRequested event) {
		sender.send(event.email());
	}

}
