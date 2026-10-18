package com.helpdesk.common;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.helpdesk.email.EmailSender;
import com.helpdesk.email.OutboundEmail;

/** Replaces the logging sender in tests, so they can check what would have been emailed. */
public class RecordingEmailSender implements EmailSender {

	private final List<OutboundEmail> sent = new CopyOnWriteArrayList<>();

	@Override
	public void send(OutboundEmail email) {
		sent.add(email);
	}

	public List<OutboundEmail> sent() {
		return sent;
	}

	public void clear() {
		sent.clear();
	}

}
