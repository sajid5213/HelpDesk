package com.helpdesk.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

	/** Looks up by email, ignoring case and surrounding spaces. */
	default Optional<User> findByEmailIgnoringCase(String email) {
		return findByEmail(User.normalizeEmail(email));
	}

	default boolean existsByEmailIgnoringCase(String email) {
		return existsByEmail(User.normalizeEmail(email));
	}

	/** Exact match on the stored (already normalized) email. Prefer {@link #findByEmailIgnoringCase}. */
	Optional<User> findByEmail(String normalizedEmail);

	boolean existsByEmail(String normalizedEmail);

}
