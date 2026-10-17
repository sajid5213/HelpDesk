package com.helpdesk.auth;

import java.net.HttpCookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.helpdesk.common.IntegrationTest;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieTest extends IntegrationTest {

	private static final String PASSWORD = "correct-horse-battery";

	private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
	};

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	void browserLoginSetsHttpOnlyStrictCookieAndOmitsBodyToken() {
		EntityExchangeResult<Map<String, Object>> result = browserLogin(newUser());

		String setCookie = result.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).startsWith("refresh_token=")
			.contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/auth", "Max-Age=604800");
		assertThat(result.getResponseBody()).containsKey("accessToken").doesNotContainKey("refreshToken");
	}

	@Test
	void browserRefreshUsesCookieAndRotatesIt() {
		String first = cookieValue(browserLogin(newUser()));

		EntityExchangeResult<Map<String, Object>> refreshed = client.post()
			.uri("/auth/refresh")
			.header("X-Auth-Client", "browser")
			.cookie("refresh_token", first)
			.exchange()
			.expectStatus()
			.isOk()
			.returnResult(JSON_OBJECT);

		String second = cookieValue(refreshed);
		assertThat(second).isNotBlank().isNotEqualTo(first);
		assertThat(refreshed.getResponseBody()).containsKey("accessToken").doesNotContainKey("refreshToken");
	}

	@Test
	void cookieRefreshWithoutClientHeaderIsRejected() {
		String token = cookieValue(browserLogin(newUser()));

		client.post()
			.uri("/auth/refresh")
			.cookie("refresh_token", token)
			.exchange()
			.expectStatus()
			.isUnauthorized();

		// The cookie was not consumed: the browser can still use it properly.
		client.post()
			.uri("/auth/refresh")
			.header("X-Auth-Client", "browser")
			.cookie("refresh_token", token)
			.exchange()
			.expectStatus()
			.isOk();
	}

	@Test
	void browserLogoutClearsCookieAndRevokesToken() {
		String token = cookieValue(browserLogin(newUser()));

		EntityExchangeResult<Void> logout = client.post()
			.uri("/auth/logout")
			.header("X-Auth-Client", "browser")
			.cookie("refresh_token", token)
			.exchange()
			.expectStatus()
			.isNoContent()
			.returnResult(Void.class);

		HttpCookie cleared = HttpCookie.parse(logout.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE)).get(0);
		assertThat(cleared.getName()).isEqualTo("refresh_token");
		assertThat(cleared.getMaxAge()).isZero();
		client.post()
			.uri("/auth/refresh")
			.header("X-Auth-Client", "browser")
			.cookie("refresh_token", token)
			.exchange()
			.expectStatus()
			.isUnauthorized();
	}

	private EntityExchangeResult<Map<String, Object>> browserLogin(User user) {
		return client.post()
			.uri("/auth/token")
			.header("X-Auth-Client", "browser")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("email", user.getEmail(), "password", PASSWORD))
			.exchange()
			.expectStatus()
			.isOk()
			.returnResult(JSON_OBJECT);
	}

	private static String cookieValue(EntityExchangeResult<?> result) {
		List<HttpCookie> cookies = HttpCookie.parse(result.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE));
		return cookies.get(0).getValue();
	}

	private User newUser() {
		return users.save(new User("user-" + UUID.randomUUID() + "@helpdesk.test", passwordEncoder.encode(PASSWORD),
				Role.AGENT, clock.instant()));
	}

}
