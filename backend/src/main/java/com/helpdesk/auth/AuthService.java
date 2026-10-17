package com.helpdesk.auth;

import java.util.Optional;

import com.helpdesk.audit.AuditService;
import com.helpdesk.audit.AuthEventType;
import com.helpdesk.auth.AccessTokenIssuer.IssuedAccessToken;
import com.helpdesk.common.ApiException;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Login, refresh, and logout. Every outcome is written to the audit log. */
@Service
public class AuthService {

	static final String INVALID_CREDENTIALS = "invalid_credentials";

	static final String INVALID_REFRESH_TOKEN = "invalid_refresh_token";

	static final String ACCOUNT_LOCKED = "account_locked";

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final AccessTokenIssuer accessTokens;

	private final RefreshTokenService refreshTokens;

	private final AuditService audit;

	private final LoginRateLimiter rateLimiter;

	private final LoginAttemptService attempts;

	/** Compared against when the email is unknown, so both cases take the same time. */
	private final String dummyHash;

	AuthService(UserRepository users, PasswordEncoder passwordEncoder, AccessTokenIssuer accessTokens,
			RefreshTokenService refreshTokens, AuditService audit, LoginRateLimiter rateLimiter,
			LoginAttemptService attempts) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.accessTokens = accessTokens;
		this.refreshTokens = refreshTokens;
		this.audit = audit;
		this.rateLimiter = rateLimiter;
		this.attempts = attempts;
		this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
	}

	/** Order matters: rate limit, then lockout, then the (slow) password check. */
	public TokenPair login(String email, String password, String ip) {
		rateLimiter.check(ip, email);
		Optional<User> found = users.findByEmailIgnoringCase(email);
		if (found.isEmpty()) {
			passwordEncoder.matches(password, dummyHash);
			audit.record(AuthEventType.LOGIN_FAILURE, null, ip);
			throw invalidCredentials();
		}
		User user = found.get();
		if (attempts.isLocked(user)) {
			// Trade-off: this tells a caller the account exists. Accepted, because a user who is
			// locked out needs to know why the correct password is refused.
			audit.record(AuthEventType.LOGIN_FAILURE, user.getId(), ip);
			throw new ApiException(HttpStatus.UNAUTHORIZED, ACCOUNT_LOCKED,
					"Too many failed attempts. The account is locked for a few minutes.");
		}
		if (!passwordEncoder.matches(password, user.getPasswordHash()) || !user.isActive()) {
			attempts.onFailure(user);
			audit.record(AuthEventType.LOGIN_FAILURE, user.getId(), ip);
			throw invalidCredentials();
		}
		attempts.onSuccess(user);
		audit.record(AuthEventType.LOGIN_SUCCESS, user.getId(), ip);
		return issueFor(user, refreshTokens.issue(user));
	}

	public TokenPair refresh(String rawRefreshToken, String ip) {
		RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken, ip)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, INVALID_REFRESH_TOKEN,
					"The refresh token is invalid or has expired"));
		audit.record(AuthEventType.TOKEN_REFRESH, rotation.user().getId(), ip);
		return issueFor(rotation.user(), rotation.refreshToken());
	}

	public void logout(String rawRefreshToken, String ip) {
		Optional<java.util.UUID> owner = refreshTokens.ownerOf(rawRefreshToken);
		refreshTokens.revoke(rawRefreshToken);
		owner.ifPresent((userId) -> audit.record(AuthEventType.LOGOUT, userId, ip));
	}

	private TokenPair issueFor(User user, String refreshToken) {
		return new TokenPair(accessTokens.issue(user), refreshToken);
	}

	private static ApiException invalidCredentials() {
		// Same answer for unknown email, wrong password, and deactivated account.
		return new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS, "Email or password is incorrect");
	}

	public record TokenPair(IssuedAccessToken accessToken, String refreshToken) {
	}

}
