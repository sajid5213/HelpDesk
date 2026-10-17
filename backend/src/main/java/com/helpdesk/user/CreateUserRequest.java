package com.helpdesk.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code role} is optional and defaults to AGENT. */
public record CreateUserRequest(@NotBlank @Email String email,
		@NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH) String password,
		Role role) {

	/** Trims before validation, so " Agent@Example.com " is accepted and then normalized. */
	public CreateUserRequest {
		email = email == null ? null : email.trim();
	}

	@Override
	public String toString() {
		return "CreateUserRequest[email=" + email + ", password=<redacted>, role=" + role + "]";
	}

}
