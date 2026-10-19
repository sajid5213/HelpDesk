package com.helpdesk.common;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeEach;

import com.helpdesk.TestcontainersConfiguration;
import com.helpdesk.auth.LoginRateLimiter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Base class for integration tests: full app on a random port, backed by a real
 * PostgreSQL container shared across the test run, with a clock tests can move.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
// Keep the Prometheus endpoint on, as in production (tests turn metric exporters off by default).
@AutoConfigureMetrics
@Import({ TestcontainersConfiguration.class, IntegrationTest.TestClockConfig.class, TestUsers.class })
public abstract class IntegrationTest {

	@Autowired
	protected RestTestClient client;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected TestUsers testUsers;

	@Autowired
	private LoginRateLimiter rateLimiter;

	@Autowired
	protected FakeAiClient fakeAi;

	@Autowired
	protected RecordingEmailSender sentEmails;

	@LocalManagementPort
	private int managementPort;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/** Waits up to 15 seconds for something a background job does (send an email, run the AI). */
	protected static void eventually(BooleanSupplier condition) {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
		while (!condition.getAsBoolean()) {
			if (Instant.now().isAfter(deadline)) {
				throw new AssertionError("condition not met within 15 seconds");
			}
			try {
				Thread.sleep(50);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(ex);
			}
		}
	}

	/** A client for the private management port (actuator: health, prometheus). */
	protected RestTestClient management() {
		return RestTestClient.bindToServer().baseUrl("http://localhost:" + managementPort).build();
	}

	@BeforeEach
	void resetSharedState() {
		// Emails and AI runs are background jobs: let the previous test's due jobs finish first, so
		// they can't deliver into this test's mailbox or change its tickets. (Jobs a moved test
		// clock put in the far future never run, so they're not waited for.)
		eventually(() -> jdbcTemplate.queryForObject("""
				select count(*) from scheduled_tasks
				where task_name in ('send-email', 'process-ticket')
				  and execution_time < now() + interval '1 minute'
				""", Integer.class) == 0);
		clock.reset();
		// Every test request comes from 127.0.0.1, so per-IP limits would leak between tests.
		rateLimiter.reset();
		fakeAi.reset();
		sentEmails.clear();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock();
		}

		@Bean
		@Primary
		FakeAiClient fakeAiClient() {
			return new FakeAiClient();
		}

		@Bean
		@Primary
		RecordingEmailSender recordingEmailSender() {
			return new RecordingEmailSender();
		}

	}

}
