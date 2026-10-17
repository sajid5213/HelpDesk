package com.helpdesk.keys;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Makes sure a signing key exists before the app starts taking logins. */
@Component
@Order(0)
class SigningKeyBootstrap implements ApplicationRunner {

	private final SigningKeyService signingKeys;

	SigningKeyBootstrap(SigningKeyService signingKeys) {
		this.signingKeys = signingKeys;
	}

	@Override
	public void run(ApplicationArguments args) {
		signingKeys.ensureActiveKey();
	}

}
