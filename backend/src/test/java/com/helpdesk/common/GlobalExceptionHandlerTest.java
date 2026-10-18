package com.helpdesk.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

// RFC 9457: an absent "type" means "about:blank", so Spring omits it for these generic errors.
@WebMvcTest(GlobalExceptionHandlerTest.ErrorTestController.class)
@Import(GlobalExceptionHandlerTest.ErrorTestController.class)
@WithMockUser
class GlobalExceptionHandlerTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void validationErrorReturns400ProblemDetail() {
		assertThat(mvc.post()
			.uri("/test/errors/validated")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"\"}")
			.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
			.hasStatus(400)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.satisfies((json) -> {
				json.assertThat().extractingPath("$.status").isEqualTo(400);
				json.assertThat().extractingPath("$.title").isEqualTo("Bad Request");
				json.assertThat().doesNotHavePath("$.type");
				json.assertThat().extractingPath("$.detail").isEqualTo("Some fields are missing or invalid.");
				json.assertThat().extractingPath("$.errors.name").isEqualTo("must not be blank");
				json.assertThat().extractingPath("$.instance").isEqualTo("/test/errors/validated");
			});
	}

	@Test
	void notFoundExceptionReturns404ProblemDetail() {
		assertThat(mvc.get().uri("/test/errors/missing"))
			.hasStatus(404)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.satisfies((json) -> {
				json.assertThat().extractingPath("$.status").isEqualTo(404);
				json.assertThat().extractingPath("$.title").isEqualTo("Not Found");
				json.assertThat().doesNotHavePath("$.type");
				json.assertThat().extractingPath("$.detail").isEqualTo("Ticket 42 not found");
				json.assertThat().extractingPath("$.instance").isEqualTo("/test/errors/missing");
			});
	}

	@Test
	void unknownPathReturns404ProblemDetail() {
		assertThat(mvc.get().uri("/no-such-endpoint"))
			.hasStatus(404)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
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
