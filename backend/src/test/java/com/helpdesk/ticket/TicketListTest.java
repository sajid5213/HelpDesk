package com.helpdesk.ticket;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.helpdesk.common.IntegrationTest;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TicketFixtures.class)
class TicketListTest extends IntegrationTest {

	private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
	};

	@Autowired
	TicketFixtures fixtures;

	@Test
	void agentSeesOnlyOwnAndUnassignedEvenWithAssigneeAny() {
		User agent = testUsers.create(Role.AGENT);
		String tag = tag();
		Ticket mine = fixtures.create(tag + " mine", agent.getId());
		Ticket open = fixtures.create(tag + " unassigned", null);
		fixtures.create(tag + " someone else's", testUsers.create(Role.AGENT).getId());

		assertThat(ids(list(testUsers.bearer(agent), "q=" + tag + "&assignee=any")))
			.containsExactlyInAnyOrder(mine.getId(), open.getId());
		assertThat(ids(list(testUsers.bearer(agent), "q=" + tag + "&assignee=MINE"))).containsExactly(mine.getId());
		assertThat(ids(list(testUsers.bearer(agent), "q=" + tag + "&assignee=unassigned"))).containsExactly(open.getId());
	}

	@Test
	void adminSeesAll() {
		String tag = tag();
		fixtures.create(tag + " a", testUsers.create(Role.AGENT).getId());
		fixtures.create(tag + " b", null);
		fixtures.create(tag + " c", testUsers.create(Role.AGENT).getId());

		assertThat(ids(list(admin(), "q=" + tag))).hasSize(3);
	}

	@Test
	void filtersByStatusAndCategoryIncludingUnclassified() {
		String tag = tag();
		Ticket resolved = fixtures.create(tag + " resolved", null);
		resolved.resolve(clock.instant());
		fixtures.save(resolved);
		Ticket refund = fixtures.create(tag + " refund", null);
		refund.changeCategory(TicketCategory.REFUND_REQUEST, clock.instant());
		fixtures.save(refund);
		Ticket unclassified = fixtures.create(tag + " none", null);

		assertThat(ids(list(admin(), "q=" + tag + "&status=RESOLVED"))).containsExactly(resolved.getId());
		assertThat(ids(list(admin(), "q=" + tag + "&category=REFUND_REQUEST"))).containsExactly(refund.getId());
		assertThat(ids(list(admin(), "q=" + tag + "&category=unclassified")))
			.containsExactlyInAnyOrder(resolved.getId(), unclassified.getId());
	}

	@Test
	void searchMatchesSubjectOrEmailIgnoringCase() {
		String tag = tag();
		Ticket bySubject = fixtures.create("Video player " + tag, null);

		assertThat(ids(list(admin(), "q=" + tag.toUpperCase()))).containsExactly(bySubject.getId());
		assertThat(ids(list(admin(), "q=" + bySubject.getStudentEmail()))).containsExactly(bySubject.getId());
	}

	@Test
	void searchTreatsWildcardsLiterally() {
		assertThat(ids(list(admin(), "q=%25%25" + tag()))).isEmpty();
	}

	@Test
	void newestFirstByDefaultAndSortByUpdatedAt() {
		String tag = tag();
		Ticket older = fixtures.create(tag + " older", null);
		clock.advance(Duration.ofMinutes(1));
		Ticket newer = fixtures.create(tag + " newer", null);

		assertThat(ids(list(admin(), "q=" + tag))).containsExactly(newer.getId(), older.getId());

		clock.advance(Duration.ofMinutes(1));
		older.touch(clock.instant());
		fixtures.save(older);
		assertThat(ids(list(admin(), "q=" + tag + "&sort=updatedAt,desc"))).containsExactly(older.getId(), newer.getId());
	}

	@Test
	void pageSizeCappedAt100() {
		client.get()
			.uri("/api/tickets?size=1000")
			.header("Authorization", admin())
			.exchange()
			.expectBody()
			.jsonPath("$.page.size").isEqualTo(100);
	}

	@Test
	void unknownSortFieldOrFilterValueReturns400() {
		client.get().uri("/api/tickets?sort=studentEmail").header("Authorization", admin()).exchange()
			.expectStatus().isBadRequest()
			.expectBody().jsonPath("$.error").isEqualTo("invalid_sort");
		client.get().uri("/api/tickets?category=BILLING").header("Authorization", admin()).exchange()
			.expectStatus().isBadRequest();
		client.get().uri("/api/tickets?assignee=everyone").header("Authorization", admin()).exchange()
			.expectStatus().isBadRequest();
	}

	private Map<String, Object> list(String bearer, String query) {
		return client.get()
			.uri("/api/tickets?" + query)
			.header("Authorization", bearer)
			.exchange()
			.expectStatus()
			.isOk()
			.returnResult(JSON_OBJECT)
			.getResponseBody();
	}

	@SuppressWarnings("unchecked")
	private static List<Long> ids(Map<String, Object> page) {
		return ((List<Map<String, Object>>) page.get("content")).stream()
			.map((t) -> ((Number) t.get("id")).longValue())
			.toList();
	}

	private String admin() {
		return testUsers.bearer(testUsers.create(Role.ADMIN));
	}

	/** A unique word to search for, so each test only sees its own tickets. */
	private static String tag() {
		return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

}
