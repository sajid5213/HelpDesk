package com.helpdesk.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JsonAuthEntryPoint entryPoint,
			JsonAccessDeniedHandler accessDeniedHandler) {
		http.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// Bearer-token API: no cookies authenticate requests, so CSRF protection does not apply.
			// The refresh cookie (Phase 2.6) is guarded separately by SameSite=Strict plus a required header.
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache((cache) -> cache.disable())
			.authorizeHttpRequests((requests) -> requests
				.requestMatchers("/auth/token", "/auth/refresh", "/auth/logout").permitAll()
				.requestMatchers("/.well-known/jwks.json").permitAll()
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				// API docs. Public for local development; Phase 7 decides whether production exposes them.
				.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
				.requestMatchers("/error").permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer((resourceServer) -> resourceServer
				.bearerTokenResolver(bearerTokenResolver())
				.jwt((jwt) -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
				.authenticationEntryPoint(entryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.exceptionHandling((exceptions) -> exceptions.authenticationEntryPoint(entryPoint)
				.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

	/** Maps the "roles" claim (["ADMIN"]) to Spring authorities (ROLE_ADMIN). */
	static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("roles");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	/**
	 * Ignores the Authorization header on the token endpoints and JWKS. A client whose access
	 * token just expired must still be able to call /auth/refresh with that header attached.
	 */
	static BearerTokenResolver bearerTokenResolver() {
		DefaultBearerTokenResolver standard = new DefaultBearerTokenResolver();
		return (request) -> {
			String path = request.getRequestURI().substring(request.getContextPath().length());
			if (path.startsWith("/auth/") || path.equals("/.well-known/jwks.json")) {
				return null;
			}
			return standard.resolve(request);
		};
	}

}
