package com.helpdesk.keys;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SigningKeyRepository extends JpaRepository<SigningKey, String> {

	Optional<SigningKey> findByStatus(KeyStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select k from SigningKey k where k.status = com.helpdesk.keys.KeyStatus.ACTIVE")
	Optional<SigningKey> lockActive();

	List<SigningKey> findAllByOrderByCreatedAtDesc();

}
