package com.helpdesk.auth;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.helpdesk.common.IntegrationTest;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

import static org.assertj.core.api.Assertions.assertThat;

class LoginProtectionTest extends IntegrationTest {

	private static final String PASSWORD = "correct-horse-battery";

	/** Enough to refill a one-minute token bucket, far less than the 15-minute lock. */
	private static final Duration NEXT_MINUTE = Duration.ofSeconds(61);

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	void sixthAttemptPerAccountWithinMinuteReturns429() {
		User user = newUser();
		for (int i = 0; i < 5; i++) {
			login(user.getEmail(), "wrong-password").expectStatus().isUnauthorized();
		}

		login(user.getEmail(), "wrong-password").expectStatus()
			.isEqualTo(429)
			.expectHeader()
			.exists("Retry-After")
			.expectBody()
			.jsonPath("$.error").isEqualTo("rate_limited");
	}

	@Test
	void perIpLimitAppliesAcrossDifferentAccounts() {
		for (int i = 0; i < 20; i++) {
			login(randomEmail(), "whatever").expectStatus().isUnauthorized();
		}

		login(randomEmail(), "whatever").expectStatus().isEqualTo(429);
	}

	@Test
	void rateLimitResetsAfterTheWindow() {
		User user = newUser();
		for (int i = 0; i < 5; i++) {
			login(user.getEmail(), "wrong-password");
		}
		login(user.getEmail(), "wrong-password").expectStatus().isEqualTo(429);

		clock.advance(NEXT_MINUTE);

		login(user.getEmail(), "wrong-password").expectStatus().isUnauthorized();
	}

	@Test
	void fiveFailuresLockAccountEvenForCorrectPassword() {
		User user = newUser();
		for (int i = 0; i < 5; i++) {
			login(user.getEmail(), "wrong-password").expectStatus().isUnauthorized();
		}
		clock.advance(NEXT_MINUTE);

		login(user.getEmail(), PASSWORD).expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.error").isEqualTo("account_locked");
	}

	@Test
	void lockExpiresAfterDuration() {
		User user = newUser();
		for (int i = 0; i < 5; i++) {
			login(user.getEmail(), "wrong-password");
		}

		clock.advance(Duration.ofMinutes(15).plusSeconds(1));

		login(user.getEmail(), PASSWORD).expectStatus().isOk();
	}

	@Test
	void successResetsFailureCount() {
		User user = newUser();
		for (int i = 0; i < 4; i++) {
			login(user.getEmail(), "wrong-password").expectStatus().isUnauthorized();
		}
		login(user.getEmail(), PASSWORD).expectStatus().isOk();
		assertThat(users.findById(user.getId()).orElseThrow().getFailedLoginCount()).isZero();

		clock.advance(NEXT_MINUTE);
		for (int i = 0; i < 4; i++) {
			login(user.getEmail(), "wrong-password").expectStatus().isUnauthorized();
		}

		// 4 + 4 failures, but never 5 in a row: still not locked.
		login(user.getEmail(), PASSWORD).expectStatus().isOk();
	}

	private ResponseSpec login(String email, String password) {
		return client.post()
			.uri("/auth/token")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("email", email, "password", password))
			.exchange();
	}

	private User newUser() {
		return users.save(new User(randomEmail(), passwordEncoder.encode(PASSWORD), Role.AGENT, clock.instant()));
	}

	private static String randomEmail() {
		return "user-" + UUID.randomUUID() + "@helpdesk.test";
	}

}
