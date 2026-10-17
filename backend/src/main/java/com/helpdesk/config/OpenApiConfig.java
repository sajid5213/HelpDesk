package com.helpdesk.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html. Click "Authorize" and paste an access token from POST /auth/token. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	static final String BEARER = "bearerAuth";

	@Bean
	OpenAPI helpdeskOpenApi() {
		return new OpenAPI()
			.info(new Info().title("HelpDesk API")
				.version("v1")
				.description("AI-assisted support tickets. Log in with POST /auth/token, then authorize with the "
						+ "access token. /auth/** and /.well-known/jwks.json need no token."))
			.components(new Components().addSecuritySchemes(BEARER,
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER));
	}

}
