package com.helpdesk.auth;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.helpdesk.common.ProblemWriter;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 401 for missing or invalid credentials, as problem+json. */
@Component
class JsonAuthEntryPoint implements AuthenticationEntryPoint {

	private final ProblemWriter problemWriter;

	JsonAuthEntryPoint(ProblemWriter problemWriter) {
		this.problemWriter = problemWriter;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		// Same message for every failure (missing, expired, bad signature...) so callers learn nothing extra.
		response.setHeader("WWW-Authenticate", "Bearer");
		problemWriter.write(request, response, HttpStatus.UNAUTHORIZED, "Authentication is required");
	}

}
