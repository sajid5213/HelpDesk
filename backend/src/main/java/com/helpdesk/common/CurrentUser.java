package com.helpdesk.common;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** The caller, read from the validated access token: user id ({@code sub}) and whether they are an admin. */
public record CurrentUser(UUID id, boolean admin) {

	public static CurrentUser from(Authentication authentication) {
		Jwt jwt = (Jwt) authentication.getPrincipal();
		boolean admin = authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.anyMatch("ROLE_ADMIN"::equals);
		return new CurrentUser(UUID.fromString(jwt.getSubject()), admin);
	}

}
