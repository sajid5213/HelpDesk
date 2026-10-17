package com.helpdesk.user;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helpdesk.config.HelpdeskProperties;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first admin from ADMIN_EMAIL / ADMIN_PASSWORD. Never overwrites an existing account. */
@Component
public class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final HelpdeskProperties.Admin admin;

	private final Clock clock;

	AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, HelpdeskProperties properties,
			Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.admin = properties.admin();
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (admin == null || isBlank(admin.email()) || isBlank(admin.password())) {
			throw new IllegalStateException("ADMIN_EMAIL and ADMIN_PASSWORD must be set to create the first admin");
		}
		if (users.existsByEmailIgnoringCase(admin.email())) {
			return;
		}
		if (!PasswordPolicy.isAcceptable(admin.password())) {
			throw new IllegalStateException("ADMIN_PASSWORD must be at least " + PasswordPolicy.MIN_LENGTH + " characters");
		}
		users.save(new User(admin.email(), passwordEncoder.encode(admin.password()), Role.ADMIN, clock.instant()));
		log.info("Created first admin account {}", admin.email());
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
