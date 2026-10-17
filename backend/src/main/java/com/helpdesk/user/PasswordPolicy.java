package com.helpdesk.user;

/** One place for password rules, used by the first-admin bootstrap and the user API. */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 12;

	public static final int MAX_LENGTH = 128;

	private PasswordPolicy() {
	}

	public static boolean isAcceptable(String password) {
		return password != null && password.length() >= MIN_LENGTH && password.length() <= MAX_LENGTH;
	}

}
