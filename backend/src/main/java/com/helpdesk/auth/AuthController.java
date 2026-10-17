package com.helpdesk.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.helpdesk.auth.AuthService.TokenPair;
import com.helpdesk.config.HelpdeskProperties;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token endpoints. API clients (Swagger, Bruno, other services) get the refresh token in the
 * JSON body; browsers sending {@code X-Auth-Client: browser} get it only as an HttpOnly cookie.
 */
@RestController
@RequestMapping("/auth")
class AuthController {

	private final AuthService auth;

	private final AuthCookieWriter cookies;

	/** expires_in is the access-token lifetime in seconds, as in OAuth 2 (RFC 6749 section 5.1). */
	private final long expiresInSeconds;

	AuthController(AuthService auth, AuthCookieWriter cookies, HelpdeskProperties properties) {
		this.auth = auth;
		this.cookies = cookies;
		this.expiresInSeconds = properties.auth().accessTokenTtl().toSeconds();
	}

	@PostMapping("/token")
	TokenResponse token(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		return respond(auth.login(body.email(), body.password(), request.getRemoteAddr()), request, response);
	}

	@PostMapping("/refresh")
	TokenResponse refresh(@RequestBody(required = false) RefreshRequest body,
			@CookieValue(name = AuthCookieWriter.COOKIE_NAME, required = false) String cookie,
			HttpServletRequest request, HttpServletResponse response) {
		String refreshToken = refreshTokenFrom(body, cookie, request);
		return respond(auth.refresh(refreshToken, request.getRemoteAddr()), request, response);
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest body,
			@CookieValue(name = AuthCookieWriter.COOKIE_NAME, required = false) String cookie,
			HttpServletRequest request, HttpServletResponse response) {
		auth.logout(refreshTokenFrom(body, cookie, request), request.getRemoteAddr());
		if (cookies.isBrowser(request)) {
			cookies.clear(response);
		}
		return ResponseEntity.noContent().build();
	}

	/** The body wins; the cookie is only read when the request declares itself a browser. */
	private String refreshTokenFrom(RefreshRequest body, String cookie, HttpServletRequest request) {
		if (body != null && body.refreshToken() != null && !body.refreshToken().isBlank()) {
			return body.refreshToken();
		}
		return cookies.isBrowser(request) ? cookie : null;
	}

	private TokenResponse respond(TokenPair pair, HttpServletRequest request, HttpServletResponse response) {
		String refreshInBody = pair.refreshToken();
		if (cookies.isBrowser(request)) {
			cookies.write(response, pair.refreshToken());
			refreshInBody = null;
		}
		return new TokenResponse(pair.accessToken().token(), "Bearer", expiresInSeconds, refreshInBody);
	}

	record LoginRequest(@NotBlank String email, @NotBlank String password) {

		@Override
		public String toString() {
			return "LoginRequest[email=" + email + ", password=<redacted>]";
		}

	}

	record RefreshRequest(String refreshToken) {

		@Override
		public String toString() {
			return "RefreshRequest[refreshToken=<redacted>]";
		}

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken) {
	}

}
