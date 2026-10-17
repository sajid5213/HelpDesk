package com.helpdesk.common;

import java.util.Locale;

/** Emails are compared case-insensitively by storing them trimmed and lowercased. */
public final class Emails {

	private Emails() {
	}

	public static String normalize(String email) {
		return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}

}
