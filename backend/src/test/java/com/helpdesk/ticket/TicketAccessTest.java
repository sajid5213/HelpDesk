package com.helpdesk.ticket;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

import com.helpdesk.common.IntegrationTest;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/** The ticket access rules from tech-stack.md section 3, plus claiming. */
@Import(TicketFixtures.class)
class TicketAccessTest extends IntegrationTest {

	@Autowired
	TicketFixtures fixtures;

	@Test
	void agentReadingAnotherAgentsTicketGets403() {
		User owner = testUsers.create(Role.AGENT);
		Ticket ticket = fixtures.assignedTo(owner.getId());

		get(ticket, testUsers.bearer(testUsers.create(Role.AGENT))).expectStatus()
			.isForbidden()
			.expectBody()
			.jsonPath("$.status").isEqualTo(403);
	}

	@Test
	void agentUpdatingAnotherAgentsTicketGets403() {
		Ticket ticket = fixtures.assignedTo(testUsers.create(Role.AGENT).getId());
		String intruder = testUsers.bearer(testUsers.create(Role.AGENT));

		reply(ticket, intruder).expectStatus().isForbidden();
		client.patch()
			.uri("/api/tickets/" + ticket.getId())
			.header("Authorization", intruder)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("status", "RESOLVED"))
			.exchange()
			.expectStatus()
			.isForbidden();
	}

	@Test
	void adminCanReadAndUpdateAnyTicket() {
		Ticket ticket = fixtures.assignedTo(testUsers.create(Role.AGENT).getId());
		String admin = testUsers.bearer(testUsers.create(Role.ADMIN));

		get(ticket, admin).expectStatus().isOk();
		reply(ticket, admin).expectStatus().isCreated();
	}

	@Test
	void agentCanReadAndClaimUnassignedTicket() {
		User agent = testUsers.create(Role.AGENT);
		String bearer = testUsers.bearer(agent);
		Ticket ticket = fixtures.unassigned();

		get(ticket, bearer).expectStatus().isOk();
		claim(ticket, bearer).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.assigneeId").isEqualTo(agent.getId().toString());
		reply(ticket, bearer).expectStatus().isCreated();
	}

	@Test
	void agentCannotReplyToUnclaimedTicket() {
		reply(fixtures.unassigned(), testUsers.bearer(testUsers.create(Role.AGENT))).expectStatus().isForbidden();
	}

	@Test
	void claimingAnAssignedTicketReturns409() {
		Ticket ticket = fixtures.assignedTo(testUsers.create(Role.AGENT).getId());

		claim(ticket, testUsers.bearer(testUsers.create(Role.AGENT))).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.error").isEqualTo("already_assigned");
	}

	@Test
	void claimingYourOwnTicketAgainIsHarmless() {
		User agent = testUsers.create(Role.AGENT);
		Ticket ticket = fixtures.assignedTo(agent.getId());

		claim(ticket, testUsers.bearer(agent)).expectStatus().isOk();
	}

	@Test
	void concurrentClaimsExactlyOneWins() {
		Ticket ticket = fixtures.unassigned();
		List<String> agents = List.of(testUsers.bearer(testUsers.create(Role.AGENT)),
				testUsers.bearer(testUsers.create(Role.AGENT)));
		CountDownLatch start = new CountDownLatch(1);

		List<CompletableFuture<Integer>> attempts = agents.stream().map((bearer) -> CompletableFuture.supplyAsync(() -> {
			try {
				start.await();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return claim(ticket, bearer).returnResult(String.class).getStatus().value();
		})).toList();
		start.countDown();

		assertThat(attempts.stream().map(CompletableFuture::join).toList()).containsExactlyInAnyOrder(200, 409);
	}

	@Test
	void unknownTicketReturns404() {
		client.get()
			.uri("/api/tickets/999999999")
			.header("Authorization", testUsers.bearer(testUsers.create(Role.ADMIN)))
			.exchange()
			.expectStatus()
			.isNotFound();
	}

	private org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec get(Ticket ticket, String bearer) {
		return client.get().uri("/api/tickets/" + ticket.getId()).header("Authorization", bearer).exchange();
	}

	private org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec claim(Ticket ticket,
			String bearer) {
		return client.post().uri("/api/tickets/" + ticket.getId() + "/claim").header("Authorization", bearer).exchange();
	}

	private org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec reply(Ticket ticket,
			String bearer) {
		return client.post()
			.uri("/api/tickets/" + ticket.getId() + "/replies")
			.header("Authorization", bearer)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("body", "Here is how to fix it."))
			.exchange();
	}

}
