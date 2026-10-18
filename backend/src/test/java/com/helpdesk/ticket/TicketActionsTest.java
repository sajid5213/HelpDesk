package com.helpdesk.ticket;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.helpdesk.common.IntegrationTest;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@Import(TicketFixtures.class)
class TicketActionsTest extends IntegrationTest {

	@Autowired
	TicketFixtures fixtures;

	@Autowired
	TicketMessageRepository messages;

	@Autowired
	TicketRepository tickets;

	private User agent;

	private String bearer;

	@BeforeEach
	void agentWithTicket() {
		agent = testUsers.create(Role.AGENT);
		bearer = testUsers.bearer(agent);
	}

	@Test
	void replyResolvesTicketRecordsMessageAndEmailsStudent() {
		Ticket ticket = fixtures.assignedTo(agent.getId());

		post(ticket, "replies", Map.of("body", "Clear your cache and reload.")).expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.status").isEqualTo("RESOLVED")
			.jsonPath("$.messages[1].direction").isEqualTo("OUTBOUND")
			.jsonPath("$.messages[1].authorUserId").isEqualTo(agent.getId().toString());

		Ticket reloaded = fixtures.reload(ticket);
		assertThat(reloaded.getFirstResponseAt()).isNotNull();
		assertThat(reloaded.getResolvedAt()).isNotNull();
		eventually(() -> !sentEmails.sent().isEmpty());
		assertThat(sentEmails.sent()).singleElement().satisfies((email) -> {
			assertThat(email.to()).isEqualTo(ticket.getStudentEmail());
			assertThat(email.subject()).isEqualTo("Re: [#" + ticket.getId() + "] " + ticket.getSubject());
			assertThat(email.body()).isEqualTo("Clear your cache and reload.");
		});
	}

	@Test
	void firstResponseTimeIsKeptOnLaterReplies() {
		Ticket ticket = fixtures.assignedTo(agent.getId());
		post(ticket, "replies", Map.of("body", "First answer"));
		var first = fixtures.reload(ticket).getFirstResponseAt();

		patch(ticket, Map.of("status", "OPEN")).expectStatus().isOk();
		post(ticket, "replies", Map.of("body", "Second answer"));

		assertThat(fixtures.reload(ticket).getFirstResponseAt()).isEqualTo(first);
	}

	@Test
	void replyToClosedTicketReturns409AndSendsNothing() {
		Ticket ticket = fixtures.assignedTo(agent.getId());
		patch(ticket, Map.of("status", "CLOSED")).expectStatus().isOk();

		post(ticket, "replies", Map.of("body", "Too late")).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.error").isEqualTo("ticket_closed");
		assertThat(sentEmails.sent()).isEmpty();
	}

	@Test
	void closedTicketCannotBeReopened() {
		Ticket ticket = fixtures.assignedTo(agent.getId());
		patch(ticket, Map.of("status", "CLOSED"));

		patch(ticket, Map.of("status", "OPEN")).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.error").isEqualTo("invalid_transition");
	}

	@Test
	void statusMovesBetweenOpenAndResolved() {
		Ticket ticket = fixtures.assignedTo(agent.getId());

		patch(ticket, Map.of("status", "RESOLVED")).expectBody().jsonPath("$.status").isEqualTo("RESOLVED");
		patch(ticket, Map.of("status", "OPEN")).expectBody()
			.jsonPath("$.status").isEqualTo("OPEN")
			.jsonPath("$.resolvedAt").doesNotExist();
	}

	@Test
	void agentCanCorrectCategoryOnOwnTicket() {
		Ticket ticket = fixtures.assignedTo(agent.getId());

		patch(ticket, Map.of("category", "TECHNICAL_QUESTION")).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.category").isEqualTo("TECHNICAL_QUESTION")
			.jsonPath("$.categorySetManually").isEqualTo(true);
	}

	@Test
	void notesAreShownOnDetailAndNotEmailed() {
		Ticket ticket = fixtures.assignedTo(agent.getId());

		post(ticket, "notes", Map.of("body", "Student is on the old course version.")).expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.notes[0].body").isEqualTo("Student is on the old course version.")
			.jsonPath("$.notes[0].authorUserId").isEqualTo(agent.getId().toString());
		assertThat(sentEmails.sent()).isEmpty();
	}

	@Test
	void onlyAdminCanReassign() {
		Ticket ticket = fixtures.assignedTo(agent.getId());
		User other = testUsers.create(Role.AGENT);

		assign(ticket, bearer, other.getId().toString()).expectStatus().isForbidden();
		assign(ticket, testUsers.bearer(testUsers.create(Role.ADMIN)), other.getId().toString()).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.assigneeId").isEqualTo(other.getId().toString());
	}

	@Test
	void adminCanUnassignAndRejectsInactiveAssignee() {
		Ticket ticket = fixtures.assignedTo(agent.getId());
		String admin = testUsers.bearer(testUsers.create(Role.ADMIN));

		assign(ticket, admin, null).expectStatus().isOk().expectBody().jsonPath("$.assigneeId").doesNotExist();
		assign(ticket, admin, java.util.UUID.randomUUID().toString()).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.error").isEqualTo("invalid_assignee");
	}

	@Test
	void emptyReplyIsRejected() {
		post(fixtures.assignedTo(agent.getId()), "replies", Map.of("body", " ")).expectStatus().isBadRequest();
	}

	@Test
	void staleCopyCannotOverwriteNewerChanges() {
		Ticket ticket = fixtures.assignedTo(agent.getId());
		Ticket staleCopy = fixtures.reload(ticket);
		patch(ticket, Map.of("status", "RESOLVED")).expectStatus().isOk();

		staleCopy.close(clock.instant());
		assertThatExceptionOfType(ObjectOptimisticLockingFailureException.class)
			.isThrownBy(() -> tickets.saveAndFlush(staleCopy));
	}

	// --- helpers ---

	private ResponseSpec post(Ticket ticket, String action, Map<String, Object> body) {
		return client.post()
			.uri("/api/tickets/" + ticket.getId() + "/" + action)
			.header("Authorization", bearer)
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.exchange();
	}

	private ResponseSpec patch(Ticket ticket, Map<String, Object> body) {
		return client.patch()
			.uri("/api/tickets/" + ticket.getId())
			.header("Authorization", bearer)
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.exchange();
	}

	private ResponseSpec assign(Ticket ticket, String bearer, String assigneeId) {
		Map<String, Object> body = new java.util.HashMap<>();
		body.put("assigneeId", assigneeId);
		return client.post()
			.uri("/api/tickets/" + ticket.getId() + "/assign")
			.header("Authorization", bearer)
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.exchange();
	}

}
