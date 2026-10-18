package com.helpdesk.user;

import java.time.Clock;
import java.util.UUID;

import com.helpdesk.common.NotFoundException;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user. The token carries only an id and roles, so the UI asks here for the email. */
@RestController
class MeController {

	private final UserRepository users;

	private final Clock clock;

	MeController(UserRepository users, Clock clock) {
		this.users = users;
		this.clock = clock;
	}

	@GetMapping("/api/me")
	UserResponse me(@AuthenticationPrincipal Jwt jwt) {
		User user = users.findById(UUID.fromString(jwt.getSubject()))
			.orElseThrow(() -> new NotFoundException("Your account no longer exists"));
		return UserResponse.of(user, clock.instant());
	}

}
