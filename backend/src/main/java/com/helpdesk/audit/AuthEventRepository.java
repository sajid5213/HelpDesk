package com.helpdesk.audit;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthEventRepository extends JpaRepository<AuthEvent, Long> {

	List<AuthEvent> findByUserIdOrderByIdAsc(UUID userId);

	List<AuthEvent> findByTypeOrderByIdAsc(AuthEventType type);

}
