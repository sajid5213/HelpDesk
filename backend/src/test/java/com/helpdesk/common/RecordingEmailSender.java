package com.helpdesk.common;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.helpdesk.email.EmailSender;
import com.helpdesk.email.OutboundEmail;

/** Replaces the logging sender in tests, so they can check what would have been emailed. */
public class RecordingEmailSender implements EmailSender {

	private final List<OutboundEmail> sent = new CopyOnWriteArrayList<>();

	private final AtomicInteger failuresLeft = new AtomicInteger();

	private final AtomicInteger attempts = new AtomicInteger();

	@Override
	public void send(OutboundEmail email) {
		attempts.incrementAndGet();
		if (failuresLeft.getAndUpdate((n) -> Math.max(0, n - 1)) > 0) {
			throw new IllegalStateException("mail server down (test)");
		}
		sent.add(email);
	}

	/** The next {@code times} sends fail, as if the mail server were down. */
	public void failNext(int times) {
		failuresLeft.set(times);
	}

	public int attempts() {
		return attempts.get();
	}

	public List<OutboundEmail> sent() {
		return sent;
	}

	public void clear() {
		sent.clear();
		failuresLeft.set(0);
		attempts.set(0);
	}

}
