package com.helpdesk.common;

import org.springframework.http.HttpHeaders;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 problem+json bodies. Spring MVC's own exceptions
 * (validation, unknown path, bad method, ...) are handled by the superclass.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Not Found");
		return problem;
	}

	/** Someone else changed the same row first; the client should reload and try again. */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail handleConcurrentUpdate(ObjectOptimisticLockingFailureException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"This item was changed by someone else. Reload it and try again.");
		problem.setProperty("error", "conflict");
		return problem;
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
		problem.setProperty("error", ex.getErrorCode());
		HttpHeaders headers = new HttpHeaders();
		ex.getHeaders().forEach(headers::add);
		return ResponseEntity.status(ex.getStatus()).headers(headers).body(problem);
	}

}
