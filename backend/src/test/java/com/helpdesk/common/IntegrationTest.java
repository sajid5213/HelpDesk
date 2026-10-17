package com.helpdesk.common;

import org.junit.jupiter.api.BeforeEach;

import com.helpdesk.TestcontainersConfiguration;
import com.helpdesk.auth.LoginRateLimiter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Base class for integration tests: full app on a random port, backed by a real
 * PostgreSQL container shared across the test run, with a clock tests can move.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
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

	@BeforeEach
	void resetSharedState() {
		clock.reset();
		// Every test request comes from 127.0.0.1, so per-IP limits would leak between tests.
		rateLimiter.reset();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock();
		}

	}

}
