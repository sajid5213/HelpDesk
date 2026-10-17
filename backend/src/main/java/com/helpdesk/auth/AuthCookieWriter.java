package com.helpdesk.auth;

import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.helpdesk.config.HelpdeskProperties;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Browser clients keep the refresh token in a cookie JavaScript cannot read. A browser marks
 * itself with {@code X-Auth-Client: browser}; the cookie is only honored together with that
 * header, which a cross-site form post cannot add (CSRF defence on top of SameSite=Strict).
 */
@Component
class AuthCookieWriter {

	static final String COOKIE_NAME = "refresh_token";

	static final String CLIENT_HEADER = "X-Auth-Client";

	static final String BROWSER = "browser";

	private static final String PATH = "/auth";

	private final Duration maxAge;

	AuthCookieWriter(HelpdeskProperties properties) {
		this.maxAge = properties.auth().refreshTokenTtl();
	}

	boolean isBrowser(HttpServletRequest request) {
		return BROWSER.equalsIgnoreCase(request.getHeader(CLIENT_HEADER));
	}

	void write(HttpServletResponse response, String refreshToken) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(refreshToken, maxAge).toString());
	}

	void clear(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
	}

	private static ResponseCookie cookie(String value, Duration maxAge) {
		return ResponseCookie.from(COOKIE_NAME, value)
			.httpOnly(true)
			.secure(true)
			.sameSite("Strict")
			.path(PATH)
			.maxAge(maxAge)
			.build();
	}

}
