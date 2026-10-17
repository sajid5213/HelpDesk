package com.helpdesk;

import com.helpdesk.common.IntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class HelpdeskApplicationTests extends IntegrationTest {

	@Autowired
	Flyway flyway;

	@Test
	void flywayAppliedBaselineMigration() {
		assertThat(flyway.info().applied())
			.extracting(MigrationInfo::getScript)
			.contains("V1__baseline.sql");
	}

	@Test
	void healthEndpointReportsUp() {
		client.get()
			.uri("/actuator/health")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.status")
			.isEqualTo("UP");
	}

}
