package com.helpdesk.audit;

import org.junit.jupiter.api.Test;

import com.helpdesk.common.IntegrationTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class AuditServiceTest extends IntegrationTest {

	@Autowired
	AuditService auditService;

	@Autowired
	AuthEventRepository events;

	@Autowired
	TransactionTemplate transactions;

	@Test
	void recordsEventWithoutUser() {
		long before = events.count();

		auditService.record(AuthEventType.LOGIN_FAILURE, null, "203.0.113.7");

		assertThat(events.count()).isEqualTo(before + 1);
	}

	@Test
	void auditRowSurvivesCallerRollback() {
		long before = events.count();

		transactions.executeWithoutResult((status) -> {
			auditService.record(AuthEventType.LOGIN_FAILURE, null, "203.0.113.8");
			status.setRollbackOnly();
		});

		assertThat(events.count()).isEqualTo(before + 1);
	}

}
