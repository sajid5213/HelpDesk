package com.helpdesk.ticket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.helpdesk.common.IntegrationTest;
import com.helpdesk.user.Role;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

import static org.assertj.core.api.Assertions.assertThat;

@RecordApplicationEvents
class InboundEmailTest extends IntegrationTest {

	private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
	};

	@Autowired
	TicketRepository tickets;

	@Autowired
	TicketMessageRepository messages;

	@Autowired
	InboundEmailService inbound;

	@Autowired
	ApplicationEvents applicationEvents;

	@Test
	void newEmailCreatesOpenTicket() {
		String student = studentEmail();

		Map<String, Object> result = send(student, "Cannot open module 3", "The video does not load.", null)
			.expectStatus()
			.isCreated()
			.expectHeader()
			.valueMatches("Location", "/api/tickets/\\d+")
			.returnResult(JSON_OBJECT)
			.getResponseBody();

		Ticket ticket = tickets.findById(id(result)).orElseThrow();
		assertThat(result).containsEntry("newTicket", true);
		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
		assertThat(ticket.getStudentEmail()).isEqualTo(student);
		assertThat(ticket.getCategory()).isNull();
		assertThat(ticket.getAssigneeId()).isNull();
		assertThat(messages.findByTicketIdOrderByIdAsc(ticket.getId())).singleElement()
			.satisfies((m) -> assertThat(m.getDirection()).isEqualTo(MessageDirection.INBOUND));
	}

	@Test
	void replyToOpenTicketAppends() {
		String student = studentEmail();
		Long ticketId = id(send(student, "Question", "First", null).returnResult(JSON_OBJECT).getResponseBody());

		Map<String, Object> second = send(student, "Re: Question", "Second", ticketId).expectStatus()
			.isCreated()
			.returnResult(JSON_OBJECT)
			.getResponseBody();

		assertThat(id(second)).isEqualTo(ticketId);
		assertThat(second).containsEntry("newTicket", false);
		assertThat(messages.findByTicketIdOrderByIdAsc(ticketId)).hasSize(2);
	}

	@Test
	void subjectMarkerThreadsTheReply() {
		String student = studentEmail();
		Long ticketId = id(send(student, "Question", "First", null).returnResult(JSON_OBJECT).getResponseBody());

		Map<String, Object> reply = send(student.toUpperCase(), "Re: [#" + ticketId + "] Question", "Second", null)
			.returnResult(JSON_OBJECT)
			.getResponseBody();

		assertThat(id(reply)).isEqualTo(ticketId);
	}

	@Test
	void replyToResolvedTicketReopensIt() {
		String student = studentEmail();
		Long ticketId = id(send(student, "Question", "First", null).returnResult(JSON_OBJECT).getResponseBody());
		Ticket ticket = tickets.findById(ticketId).orElseThrow();
		ticket.resolve(clock.instant());
		tickets.save(ticket);

		send(student, "Re: Question", "Still broken", ticketId).expectStatus().isCreated();

		Ticket reopened = tickets.findById(ticketId).orElseThrow();
		assertThat(reopened.getStatus()).isEqualTo(TicketStatus.OPEN);
		assertThat(reopened.getResolvedAt()).isNull();
	}

	@Test
	void replyToClosedTicketCreatesNewTicket() {
		String student = studentEmail();
		Long ticketId = id(send(student, "Question", "First", null).returnResult(JSON_OBJECT).getResponseBody());
		Ticket ticket = tickets.findById(ticketId).orElseThrow();
		ticket.close(clock.instant());
		tickets.save(ticket);

		Map<String, Object> reply = send(student, "Re: Question", "Again", ticketId).returnResult(JSON_OBJECT)
			.getResponseBody();

		assertThat(id(reply)).isNotEqualTo(ticketId);
		assertThat(reply).containsEntry("newTicket", true);
	}

	@Test
	void referenceFromDifferentSenderCreatesNewTicket() {
		Long ticketId = id(send(studentEmail(), "Mine", "Private details", null).returnResult(JSON_OBJECT)
			.getResponseBody());

		Map<String, Object> intruder = send(studentEmail(), "Re: [#" + ticketId + "]", "Let me in", ticketId)
			.returnResult(JSON_OBJECT)
			.getResponseBody();

		assertThat(id(intruder)).isNotEqualTo(ticketId);
		assertThat(messages.findByTicketIdOrderByIdAsc(ticketId)).hasSize(1);
	}

	@Test
	void unknownReferenceCreatesNewTicket() {
		Map<String, Object> result = send(studentEmail(), "Re: [#999999999]", "Hello", null).returnResult(JSON_OBJECT)
			.getResponseBody();

		assertThat(result).containsEntry("newTicket", true);
	}

	@Test
	void agentCannotPostInbound() {
		client.post()
			.uri("/api/inbound/emails")
			.header("Authorization", testUsers.bearer(testUsers.create(Role.AGENT)))
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("fromEmail", studentEmail(), "subject", "Hi", "body", "Hi"))
			.exchange()
			.expectStatus()
			.isForbidden();
	}

	@Test
	void invalidEmailIsRejected() {
		send("not-an-email", "Hi", "Hi", null).expectStatus().isBadRequest();
		send(studentEmail(), "", "Hi", null).expectStatus().isBadRequest();
		send(studentEmail(), "x".repeat(301), "Hi", null).expectStatus().isBadRequest();
	}

	@Test
	void storedMessagePublishesEvent() {
		InboundEmailService.Result result = inbound
			.receive(new InboundEmailRequest(studentEmail(), "Sam", "Question", "Body", null));

		assertThat(applicationEvents.stream(TicketMessageReceived.class)).singleElement().satisfies((event) -> {
			assertThat(event.ticketId()).isEqualTo(result.ticketId());
			assertThat(event.messageId()).isNotNull();
			assertThat(event.newTicket()).isTrue();
		});
	}

	// --- helpers ---

	private ResponseSpec send(String from, String subject, String body, Long ticketRef) {
		Map<String, Object> email = new HashMap<>();
		email.put("fromEmail", from);
		email.put("fromName", "Sam Student");
		email.put("subject", subject);
		email.put("body", body);
		if (ticketRef != null) {
			email.put("ticketRef", ticketRef);
		}
		return client.post()
			.uri("/api/inbound/emails")
			.header("Authorization", testUsers.bearer(testUsers.create(Role.ADMIN)))
			.contentType(MediaType.APPLICATION_JSON)
			.body(email)
			.exchange();
	}

	private static Long id(Map<String, Object> result) {
		return ((Number) result.get("ticketId")).longValue();
	}

	private static String studentEmail() {
		return "student-" + UUID.randomUUID() + "@example.com";
	}

}
