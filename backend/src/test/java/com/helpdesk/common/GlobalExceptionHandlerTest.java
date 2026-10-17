package com.helpdesk.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// RFC 9457: an absent "type" means "about:blank", so Spring omits it for these generic errors.
@Import(GlobalExceptionHandlerTest.ErrorTestController.class)
class GlobalExceptionHandlerTest extends IntegrationTest {

	@Test
	void validationErrorReturns400ProblemDetail() {
		client.post()
			.uri("/test/errors/validated")
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"name\":\"\"}")
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody()
			.jsonPath("$.status").isEqualTo(400)
			.jsonPath("$.title").isEqualTo("Bad Request")
			.jsonPath("$.type").doesNotExist()
			.jsonPath("$.detail").exists()
			.jsonPath("$.instance").isEqualTo("/test/errors/validated");
	}

	@Test
	void notFoundExceptionReturns404ProblemDetail() {
		client.get()
			.uri("/test/errors/missing")
			.exchange()
			.expectStatus()
			.isNotFound()
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody()
			.jsonPath("$.status").isEqualTo(404)
			.jsonPath("$.title").isEqualTo("Not Found")
			.jsonPath("$.type").doesNotExist()
			.jsonPath("$.detail").isEqualTo("Ticket 42 not found")
			.jsonPath("$.instance").isEqualTo("/test/errors/missing");
	}

	@Test
	void unknownPathReturns404ProblemDetail() {
		client.get()
			.uri("/no-such-endpoint")
			.exchange()
			.expectStatus()
			.isNotFound()
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody()
			.jsonPath("$.status").isEqualTo(404);
	}

	record NameRequest(@NotBlank String name) {
	}

	@RestController
	static class ErrorTestController {

		@PostMapping("/test/errors/validated")
		String validated(@Valid @RequestBody NameRequest request) {
			return request.name();
		}

		@GetMapping("/test/errors/missing")
		String missing() {
			throw new NotFoundException("Ticket 42 not found");
		}

	}

}
