package com.helpdesk.user;

import java.time.Instant;
import java.util.UUID;

/** What the API shows about an account. Never includes the password hash. */
public record UserResponse(UUID id, String email, Role role, boolean active, boolean locked, Instant createdAt) {

	static UserResponse of(User user, Instant now) {
		return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.isActive(), user.isLockedAt(now),
				user.getCreatedAt());
	}

}
