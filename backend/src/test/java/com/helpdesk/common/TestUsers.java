package com.helpdesk.common;

import java.time.Clock;
import java.util.UUID;

import com.helpdesk.auth.AccessTokenIssuer;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Creates throwaway accounts and bearer tokens for integration tests. */
@TestComponent
public class TestUsers {

	public static final String PASSWORD = "correct-horse-battery";

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final AccessTokenIssuer accessTokens;

	private final Clock clock;

	TestUsers(UserRepository users, PasswordEncoder passwordEncoder, AccessTokenIssuer accessTokens, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.accessTokens = accessTokens;
		this.clock = clock;
	}

	/** A new account with a unique email and the password {@link #PASSWORD}. */
	public User create(Role role) {
		return users.save(new User(randomEmail(), passwordEncoder.encode(PASSWORD), role, clock.instant()));
	}

	/** An {@code Authorization} header value for this user. */
	public String bearer(User user) {
		return "Bearer " + accessTokens.issue(user).token();
	}

	public static String randomEmail() {
		return "user-" + UUID.randomUUID() + "@helpdesk.test";
	}

}
