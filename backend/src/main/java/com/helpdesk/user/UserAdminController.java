package com.helpdesk.user;

import java.net.URI;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only account management. Users are deactivated, never deleted, so audit history stays intact. */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
class UserAdminController {

	private final UserService userService;

	UserAdminController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	Page<UserResponse> list(@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable) {
		return userService.list(pageable);
	}

	@GetMapping("/{id}")
	UserResponse get(@PathVariable UUID id) {
		return userService.get(id);
	}

	@PostMapping
	ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest body, @AuthenticationPrincipal Jwt admin,
			HttpServletRequest request) {
		UserResponse created = userService.create(body, adminId(admin), request.getRemoteAddr());
		return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
	}

	@PatchMapping("/{id}")
	UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest body,
			@AuthenticationPrincipal Jwt admin, HttpServletRequest request) {
		return userService.update(id, body, adminId(admin), request.getRemoteAddr());
	}

	private static UUID adminId(Jwt admin) {
		return UUID.fromString(admin.getSubject());
	}

}
