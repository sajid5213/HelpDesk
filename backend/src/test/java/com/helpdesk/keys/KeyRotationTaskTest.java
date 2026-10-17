package com.helpdesk.keys;

import java.time.Duration;
import java.time.Instant;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import org.junit.jupiter.api.Test;

import com.helpdesk.audit.AuditService;
import com.helpdesk.audit.AuthEventRepository;
import com.helpdesk.audit.AuthEventType;
import com.helpdesk.common.IntegrationTest;
import com.helpdesk.config.HelpdeskProperties;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class KeyRotationTaskTest extends IntegrationTest {

	@Autowired
	RecurringTask<Void> keyRotationRecurringTask;

	@Autowired
	SigningKeyService signingKeys;

	@Autowired
	SigningKeyRepository keys;

	@Autowired
	AuditService audit;

	@Autowired
	AuthEventRepository events;

	@Autowired
	HelpdeskProperties properties;

	@Test
	void rotationRotatesKeyAndAuditsOnce() {
		String before = signingKeys.activeKey().getKeyID();
		int auditedBefore = events.findByTypeOrderByIdAsc(AuthEventType.KEY_ROTATED).size();

		KeyRotationTask.rotate(signingKeys, audit);

		assertThat(signingKeys.activeKey().getKeyID()).isNotEqualTo(before);
		assertThat(keys.findById(before).orElseThrow().getStatus()).isEqualTo(KeyStatus.RETIRED);
		assertThat(events.findByTypeOrderByIdAsc(AuthEventType.KEY_ROTATED)).hasSize(auditedBefore + 1);
	}

	@Test
	void taskIsRegisteredWithConfiguredInterval() {
		assertThat(keyRotationRecurringTask.getName()).isEqualTo(KeyRotationTask.NAME);
		assertThat(KeyRotationTask.schedule(properties).interval()).isEqualTo(Duration.ofDays(7));
	}

	@Test
	void firstRunIsOneIntervalAfterStartNotImmediately() {
		Instant now = Instant.parse("2026-10-17T12:00:00Z");

		assertThat(KeyRotationTask.schedule(properties).getInitialExecutionTime(now))
			.isEqualTo(now.plus(Duration.ofDays(7)));
	}

}
