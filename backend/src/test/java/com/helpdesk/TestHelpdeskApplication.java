package com.helpdesk;

import org.springframework.boot.SpringApplication;

public class TestHelpdeskApplication {

	public static void main(String[] args) {
		SpringApplication.from(HelpdeskApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
