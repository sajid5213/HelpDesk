package com.helpdesk.common;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.helpdesk.ai.AiClient;
import com.helpdesk.ai.Classification;
import com.helpdesk.ai.ReplyDraft;
import com.helpdesk.ai.TicketThread;
import com.helpdesk.kb.KbArticle;

/**
 * A scripted stand-in for Claude. Off by default, so tests that do not care about AI behave as if
 * no API key were set. AI tests switch it on and say what it should answer, or throw.
 */
public class FakeAiClient implements AiClient {

	private volatile boolean enabled;

	private volatile Function<TicketThread, Classification> classify = (thread) -> {
		throw new IllegalStateException("FakeAiClient.classify not scripted");
	};

	private volatile Function<TicketThread, ReplyDraft> draft = (thread) -> {
		throw new IllegalStateException("FakeAiClient.draftReply not scripted");
	};

	private final AtomicInteger classifyCalls = new AtomicInteger();

	public void reset() {
		enabled = false;
		classifyCalls.set(0);
	}

	/** Turns the fake on with fixed answers. */
	public void answer(Classification classification, ReplyDraft reply) {
		answer((thread) -> classification, (thread) -> reply);
	}

	public void answer(Function<TicketThread, Classification> classify, Function<TicketThread, ReplyDraft> draft) {
		this.classify = classify;
		this.draft = draft;
		this.enabled = true;
	}

	/** Turns the fake on, failing every classify call with this exception. */
	public void failWith(RuntimeException failure) {
		answer((thread) -> {
			throw failure;
		}, (thread) -> {
			throw failure;
		});
	}

	public int classifyCalls() {
		return classifyCalls.get();
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public Classification classify(TicketThread thread) {
		classifyCalls.incrementAndGet();
		return classify.apply(thread);
	}

	@Override
	public ReplyDraft draftReply(TicketThread thread, List<KbArticle> knowledgeBase) {
		return draft.apply(thread);
	}

}
