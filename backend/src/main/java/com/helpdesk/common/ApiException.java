package com.helpdesk.common;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * An error with a stable machine-readable code, returned as problem+json with an
 * {@code error} property (for example {@code invalid_credentials}).
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String errorCode;

	private final Map<String, String> headers;

	public ApiException(HttpStatus status, String errorCode, String detail) {
		this(status, errorCode, detail, Map.of());
	}

	public ApiException(HttpStatus status, String errorCode, String detail, Map<String, String> headers) {
		super(detail);
		this.status = status;
		this.errorCode = errorCode;
		this.headers = Map.copyOf(headers);
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

}
