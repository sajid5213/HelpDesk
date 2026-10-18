package com.helpdesk.ticket;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.helpdesk.user.UserRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Turns staff user ids into emails for display, with one query per response. */
@Component
class StaffDirectory {

	private final UserRepository users;

	StaffDirectory(UserRepository users) {
		this.users = users;
	}

	/** A plain HashMap on purpose: callers look up null ids (unassigned, student) and get null back. */
	@Transactional(readOnly = true)
	Map<UUID, String> emails(Collection<UUID> ids) {
		Map<UUID, String> emails = new HashMap<>();
		var wanted = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
		if (!wanted.isEmpty()) {
			users.findAllById(wanted).forEach((user) -> emails.put(user.getId(), user.getEmail()));
		}
		return emails;
	}

}
