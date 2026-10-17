package com.helpdesk.auth;

import java.time.Clock;
import java.time.Duration;

import com.helpdesk.config.HelpdeskProperties;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Counts consecutive failed logins and locks the account for a while after too many. */
@Service
public class LoginAttemptService {

	private final UserRepository users;

	private final Clock clock;

	private final int maxFailures;

	private final Duration lockDuration;

	LoginAttemptService(UserRepository users, Clock clock, HelpdeskProperties properties) {
		this.users = users;
		this.clock = clock;
		this.maxFailures = properties.auth().lockout().maxFailures();
		this.lockDuration = properties.auth().lockout().duration();
	}

	public boolean isLocked(User user) {
		return user.isLockedAt(clock.instant());
	}

	@Transactional
	public void onFailure(User user) {
		users.findById(user.getId()).ifPresent((current) -> {
			int failures = current.getFailedLoginCount() + 1;
			if (failures >= maxFailures) {
				current.setLockedUntil(clock.instant().plus(lockDuration));
				current.setFailedLoginCount(0);
			}
			else {
				current.setFailedLoginCount(failures);
			}
		});
	}

	@Transactional
	public void onSuccess(User user) {
		users.findById(user.getId()).ifPresent((current) -> {
			current.setFailedLoginCount(0);
			current.setLockedUntil(null);
		});
	}

}
