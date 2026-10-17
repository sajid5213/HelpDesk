package com.helpdesk.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Every field is optional; only the ones sent are changed. */
public record UpdateUserRequest(@Email String email,
		@Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH) String password, Role role,
		Boolean active) {

	/** Trims before validation, so surrounding spaces are not an error. */
	public UpdateUserRequest {
		email = email == null ? null : email.trim();
	}

	@Override
	public String toString() {
		return "UpdateUserRequest[email=" + email + ", password=" + (password == null ? null : "<redacted>")
				+ ", role=" + role + ", active=" + active + "]";
	}

}
