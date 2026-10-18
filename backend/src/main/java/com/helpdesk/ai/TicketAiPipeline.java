package com.helpdesk.ai;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helpdesk.common.HelpdeskMetrics;
import com.helpdesk.common.HelpdeskMetrics.AiOutcome;
import com.helpdesk.config.HelpdeskProperties;
import com.helpdesk.email.OutboundEmail;
import com.helpdesk.email.OutboundEmailRequested;
import com.helpdesk.kb.KbArticle;
import com.helpdesk.kb.KbArticleRepository;
import com.helpdesk.ticket.AiStatus;
import com.helpdesk.ticket.MessageDirection;
import com.helpdesk.ticket.Ticket;
import com.helpdesk.ticket.TicketCategory;
import com.helpdesk.ticket.TicketMessage;
import com.helpdesk.ticket.TicketMessageRepository;
import com.helpdesk.ticket.TicketRepository;
import com.helpdesk.ticket.TicketService;
import com.helpdesk.ticket.TicketStatus;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What happens to every student email: classify and summarize it, draft a reply from the
 * knowledge base, then either answer automatically (only safe FAQ cases) or leave the draft for
 * an agent. Claude is called outside any database transaction.
 */
@Service
public class TicketAiPipeline {

	private static final Logger log = LoggerFactory.getLogger(TicketAiPipeline.class);

	/** Categories the AI may ever answer by itself. Refunds are never among them. */
	static final Set<TicketCategory> AUTO_REPLY_CATEGORIES = Set.of(TicketCategory.GENERAL_QUESTION,
			TicketCategory.TECHNICAL_QUESTION);

	private final AiClient ai;

	private final TicketRepository tickets;

	private final TicketMessageRepository messages;

	private final KbArticleRepository articles;

	private final ApplicationEventPublisher events;

	private final TransactionTemplate transactions;

	private final TransactionTemplate readOnly;

	/**
	 * Always a fresh transaction: markers are also written from after-commit listeners, where a
	 * normal transaction would join the already finished one and never commit.
	 */
	private final TransactionTemplate separate;

	private final Clock clock;

	private final ObservationRegistry observations;

	private final HelpdeskMetrics metrics;

	private final String mode;

	TicketAiPipeline(AiClient ai, TicketRepository tickets, TicketMessageRepository messages,
			KbArticleRepository articles, ApplicationEventPublisher events, PlatformTransactionManager txManager,
			Clock clock, ObservationRegistry observations, HelpdeskMetrics metrics, HelpdeskProperties properties) {
		this.observations = observations;
		this.metrics = metrics;
		this.mode = properties.ai().mode();
		this.ai = ai;
		this.tickets = tickets;
		this.messages = messages;
		this.articles = articles;
		this.events = events;
		this.transactions = new TransactionTemplate(txManager);
		this.readOnly = new TransactionTemplate(txManager);
		this.readOnly.setReadOnly(true);
		this.separate = new TransactionTemplate(txManager);
		this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.clock = clock;
	}

	/**
	 * Runs the AI for one stored student message. Throws to let the scheduler retry. Each attempt is
	 * one trace: the database reads, both Claude calls and the writes nest under it.
	 */
	public Outcome process(Long ticketId, boolean newTicket) {
		Outcome outcome = Observation.createNotStarted("helpdesk.ai.process", observations)
			.contextualName("ai process ticket")
			.observe(() -> run(ticketId, newTicket));
		metrics.aiOutcome(switch (outcome) {
			case AUTO_REPLIED -> AiOutcome.AUTO_RESOLVED;
			case SUGGESTED -> AiOutcome.SUGGESTED;
			case SKIPPED -> AiOutcome.SKIPPED;
		});
		return outcome;
	}

	private Outcome run(Long ticketId, boolean newTicket) {
		Snapshot snapshot = readOnly.execute((status) -> load(ticketId));
		if (snapshot == null) {
			return Outcome.SKIPPED;
		}

		Classification classification = call("classify", () -> ai.classify(snapshot.thread()));
		ReplyDraft draft = call("draft_reply", () -> ai.draftReply(snapshot.thread(), snapshot.knowledgeBase()));

		return transactions.execute((status) -> apply(ticketId, newTicket, classification, draft, snapshot));
	}

	/**
	 * One Claude call as a timer and a span, tagged with what it did and how it ended. Only fixed
	 * values become tags; the ticket text never does.
	 */
	private <T> T call(String operation, Supplier<T> call) {
		Observation observation = Observation.createNotStarted("helpdesk.ai.call", observations)
			.contextualName("claude " + operation)
			.lowCardinalityKeyValue("operation", operation)
			.lowCardinalityKeyValue("mode", mode)
			.start();
		try (Observation.Scope scope = observation.openScope()) {
			T result = call.get();
			observation.lowCardinalityKeyValue("outcome", "success");
			return result;
		}
		catch (AiRefusedException ex) {
			observation.lowCardinalityKeyValue("outcome", "refused");
			throw ex;
		}
		catch (RuntimeException ex) {
			observation.lowCardinalityKeyValue("outcome", "error");
			observation.error(ex);
			throw ex;
		}
		finally {
			observation.stop();
		}
	}

	/** Marks a ticket the AI will not handle (turned off, or Claude declined). */
	public void markSkipped(Long ticketId) {
		separate.executeWithoutResult((status) -> tickets.markAiStatus(ticketId, AiStatus.SKIPPED, clock.instant()));
		metrics.aiOutcome(AiOutcome.SKIPPED);
	}

	/** Marks a ticket whose AI job failed every retry. It stays with the agents. */
	public void markFailed(Long ticketId) {
		separate.executeWithoutResult((status) -> tickets.markAiStatus(ticketId, AiStatus.FAILED, clock.instant()));
		metrics.aiOutcome(AiOutcome.FAILED);
	}

	private Snapshot load(Long ticketId) {
		Ticket ticket = tickets.findById(ticketId).orElse(null);
		if (ticket == null || ticket.getStatus() == TicketStatus.CLOSED) {
			return null;
		}
		List<TicketMessage> thread = messages.findByTicketIdOrderByIdAsc(ticketId);
		List<TicketThread.Message> lines = thread.stream()
			.map((m) -> new TicketThread.Message(m.getDirection() == MessageDirection.INBOUND, m.getBody()))
			.toList();
		List<KbArticle> kb = articles.findAllByOrderByTitleAsc();
		return new Snapshot(new TicketThread(ticketId, ticket.getSubject(), ticket.getStudentName(), lines), kb,
				kb.stream().map(KbArticle::getId).collect(Collectors.toSet()));
	}

	private Outcome apply(Long ticketId, boolean newTicket, Classification classification, ReplyDraft draft,
			Snapshot snapshot) {
		Ticket ticket = tickets.findById(ticketId).orElse(null);
		if (ticket == null) {
			return Outcome.SKIPPED;
		}
		Instant now = clock.instant();
		ticket.applyAi(classification.category(), classification.summary(), now);

		boolean auto = mayAutoReply(ticket, newTicket, classification, draft, snapshot);
		if (auto) {
			messages.save(TicketMessage.fromAi(ticket.getId(), draft.reply(), now));
			ticket.autoResolve(now);
			events.publishEvent(new OutboundEmailRequested(new OutboundEmail(ticket.getStudentEmail(),
					TicketService.replySubject(ticket), draft.reply(), ticket.getId())));
		}
		else {
			ticket.suggestReply(draft.reply());
		}
		tickets.saveAndFlush(ticket);
		log.info("AI processed ticket {}: category={}, {}", ticketId, ticket.getCategory(),
				auto ? "answered automatically" : "reply suggested to agents");
		return auto ? Outcome.AUTO_REPLIED : Outcome.SUGGESTED;
	}

	/**
	 * The AI answers by itself only when every one of these holds. Anything else is a suggestion
	 * for an agent.
	 */
	static boolean mayAutoReply(Ticket ticket, boolean newTicket, Classification classification, ReplyDraft draft,
			Snapshot snapshot) {
		return newTicket
				&& snapshot.thread().messages().size() == 1
				&& ticket.getStatus() == TicketStatus.OPEN
				&& ticket.isUnassigned()
				&& ticket.getCategory() != null && AUTO_REPLY_CATEGORIES.contains(ticket.getCategory())
				&& !classification.mentionsRefund()
				&& draft.answeredByKb()
				&& !draft.articleIds().isEmpty()
				&& snapshot.knowledgeBaseIds().containsAll(draft.articleIds())
				&& draft.reply() != null && !draft.reply().isBlank();
	}

	record Snapshot(TicketThread thread, List<KbArticle> knowledgeBase, Set<Long> knowledgeBaseIds) {
	}

	public enum Outcome {

		AUTO_REPLIED, SUGGESTED, SKIPPED

	}

}
