package com.helpdesk.auth;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.helpdesk.common.ProblemWriter;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 403 for authenticated callers who lack permission, as problem+json. */
@Component
class JsonAccessDeniedHandler implements AccessDeniedHandler {

	private final ProblemWriter problemWriter;

	JsonAccessDeniedHandler(ProblemWriter problemWriter) {
		this.problemWriter = problemWriter;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		problemWriter.write(request, response, HttpStatus.FORBIDDEN, "You do not have permission for this action");
	}

}
