package com.helpdesk.user;

import java.time.Clock;
import java.util.UUID;

import com.helpdesk.audit.AuditService;
import com.helpdesk.audit.AuthEventType;
import com.helpdesk.common.ApiException;
import com.helpdesk.common.NotFoundException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin operations on accounts. Every change is audited with the admin as the actor. */
@Service
public class UserService {

	static final String EMAIL_TAKEN = "email_taken";

	static final String CANNOT_MODIFY_SELF = "cannot_modify_self";

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final AuditService audit;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	UserService(UserRepository users, PasswordEncoder passwordEncoder, AuditService audit,
			ApplicationEventPublisher events, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Page<UserResponse> list(Pageable pageable) {
		return users.findAll(pageable).map(this::toResponse);
	}

	@Transactional(readOnly = true)
	public UserResponse get(UUID id) {
		return toResponse(find(id));
	}

	@Transactional
	public UserResponse create(CreateUserRequest request, UUID actorId, String ip) {
		if (users.existsByEmailIgnoringCase(request.email())) {
			throw emailTaken();
		}
		Role role = request.role() == null ? Role.AGENT : request.role();
		User user = new User(request.email(), passwordEncoder.encode(request.password()), role, clock.instant());
		saveChecked(user);
		audit.record(AuthEventType.USER_CREATED, user.getId(), actorId, ip);
		return toResponse(user);
	}

	@Transactional
	public UserResponse update(UUID id, UpdateUserRequest request, UUID actorId, String ip) {
		User user = find(id);
		boolean self = user.getId().equals(actorId);
		boolean deactivating = Boolean.FALSE.equals(request.active()) && user.isActive();
		boolean reactivating = Boolean.TRUE.equals(request.active()) && !user.isActive();
		boolean roleChanging = request.role() != null && request.role() != user.getRole();

		// An admin can never lock themselves out, so at least one active admin always remains.
		if (self && (deactivating || roleChanging)) {
			throw new ApiException(HttpStatus.CONFLICT, CANNOT_MODIFY_SELF,
					"You cannot deactivate your own account or change your own role");
		}

		boolean endSessions = false;
		if (request.email() != null && !User.normalizeEmail(request.email()).equals(user.getEmail())) {
			if (users.existsByEmailIgnoringCase(request.email())) {
				throw emailTaken();
			}
			user.setEmail(request.email());
			audit.record(AuthEventType.USER_UPDATED, user.getId(), actorId, ip);
		}
		if (request.password() != null) {
			user.setPasswordHash(passwordEncoder.encode(request.password()));
			user.clearLockout();
			endSessions = true;
			audit.record(AuthEventType.PASSWORD_CHANGED, user.getId(), actorId, ip);
		}
		if (roleChanging) {
			user.setRole(request.role());
			endSessions = true;
			audit.record(AuthEventType.ROLE_CHANGED, user.getId(), actorId, ip);
		}
		if (deactivating) {
			user.setActive(false);
			endSessions = true;
			audit.record(AuthEventType.USER_DEACTIVATED, user.getId(), actorId, ip);
		}
		if (reactivating) {
			user.setActive(true);
			user.clearLockout();
			audit.record(AuthEventType.USER_REACTIVATED, user.getId(), actorId, ip);
		}

		saveChecked(user);
		if (endSessions) {
			events.publishEvent(new UserSessionsInvalidated(user.getId()));
		}
		return toResponse(user);
	}

	private User find(UUID id) {
		return users.findById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found"));
	}

	/** Flushes now, so a duplicate email from a concurrent request becomes a clean 409. */
	private void saveChecked(User user) {
		try {
			users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			throw emailTaken();
		}
	}

	private UserResponse toResponse(User user) {
		return UserResponse.of(user, clock.instant());
	}

	private static ApiException emailTaken() {
		return new ApiException(HttpStatus.CONFLICT, EMAIL_TAKEN, "Another account already uses this email");
	}

}
