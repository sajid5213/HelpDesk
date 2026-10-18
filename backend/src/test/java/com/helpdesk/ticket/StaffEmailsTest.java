package com.helpdesk.ticket;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.helpdesk.common.IntegrationTest;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

/** Ticket responses name staff by email, so the UI never has to show raw ids. */
@Import(TicketFixtures.class)
class StaffEmailsTest extends IntegrationTest {

	@Autowired
	TicketFixtures fixtures;

	@Test
	void listShowsAssigneeEmail() {
		User agent = testUsers.create(Role.AGENT);
		Ticket ticket = fixtures.create("Emails " + agent.getId(), agent.getId());

		client.get()
			.uri("/api/tickets?q=" + agent.getId())
			.header("Authorization", testUsers.bearer(agent))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.content[0].id").isEqualTo(ticket.getId())
			.jsonPath("$.content[0].assigneeEmail").isEqualTo(agent.getEmail());
	}

	@Test
	void detailShowsAssigneeAndAuthorEmails() {
		User agent = testUsers.create(Role.AGENT);
		String bearer = testUsers.bearer(agent);
		Ticket ticket = fixtures.assignedTo(agent.getId());
		post(ticket, "notes", bearer, "Checked the course version.");

		post(ticket, "replies", bearer, "Here is the fix.").expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.assigneeEmail").isEqualTo(agent.getEmail())
			.jsonPath("$.messages[0].authorEmail").doesNotExist() // the student
			.jsonPath("$.messages[1].authorEmail").isEqualTo(agent.getEmail())
			.jsonPath("$.notes[0].authorEmail").isEqualTo(agent.getEmail());
	}

	private org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec post(Ticket ticket, String action,
			String bearer, String body) {
		return client.post()
			.uri("/api/tickets/" + ticket.getId() + "/" + action)
			.header("Authorization", bearer)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("body", body))
			.exchange();
	}

}
