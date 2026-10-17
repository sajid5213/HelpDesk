package com.helpdesk.user;

import org.junit.jupiter.api.Test;

import com.helpdesk.common.IntegrationTest;
import com.helpdesk.config.HelpdeskProperties;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class AdminBootstrapTest extends IntegrationTest {

	@Autowired
	UserRepository users;

	@Autowired
	AdminBootstrap adminBootstrap;

	@Autowired
	HelpdeskProperties properties;

	@Test
	void createsAdminOnFirstStartup() {
		User admin = users.findByEmailIgnoringCase(properties.admin().email()).orElseThrow();

		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(admin.isActive()).isTrue();
		assertThat(admin.getPasswordHash()).startsWith("$2").isNotEqualTo(properties.admin().password());
	}

	@Test
	void emailLookupIgnoresCaseAndSpaces() {
		assertThat(users.findByEmailIgnoringCase("  " + properties.admin().email().toUpperCase() + " ")).isPresent();
	}

	@Test
	void doesNotDuplicateOrOverwriteAdminOnRestart() {
		String hashBefore = users.findByEmailIgnoringCase(properties.admin().email()).orElseThrow().getPasswordHash();
		long countBefore = users.count();

		adminBootstrap.run(null);

		assertThat(users.count()).isEqualTo(countBefore);
		assertThat(users.findByEmailIgnoringCase(properties.admin().email()).orElseThrow().getPasswordHash())
			.isEqualTo(hashBefore);
	}

}
