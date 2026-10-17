package com.helpdesk.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import com.helpdesk.audit.AuditService;
import com.helpdesk.audit.AuthEventType;
import com.helpdesk.config.HelpdeskProperties;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh tokens: random, stored only as SHA-256, single use. Each refresh rotates the
 * token; presenting a token that was already rotated or revoked is treated as theft and revokes
 * every token the user has.
 */
@Service
public class RefreshTokenService {

	private static final int TOKEN_BYTES = 32;

	private final RefreshTokenRepository tokens;

	private final UserRepository users;

	private final AuditService audit;

	private final Clock clock;

	private final Duration ttl;

	private final SecureRandom random = new SecureRandom();

	RefreshTokenService(RefreshTokenRepository tokens, UserRepository users, AuditService audit, Clock clock,
			HelpdeskProperties properties) {
		this.tokens = tokens;
		this.users = users;
		this.audit = audit;
		this.clock = clock;
		this.ttl = properties.auth().refreshTokenTtl();
	}

	/** Creates a new refresh token and returns the raw value. Only its hash is stored. */
	@Transactional
	public String issue(User user) {
		return create(user.getId(), clock.instant()).raw();
	}

	/**
	 * Exchanges a refresh token for a new one. Returns empty when the token is unknown,
	 * expired, reused, or belongs to an inactive user. Never throws for those cases, so the
	 * reuse revocation commits even though the caller answers 401.
	 */
	@Transactional
	public Optional<Rotation> rotate(String raw, String ip) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		Instant now = clock.instant();
		Optional<RefreshToken> found = tokens.lockByHash(hash(raw));
		if (found.isEmpty()) {
			return Optional.empty();
		}
		RefreshToken token = found.get();
		if (token.isRevoked()) {
			tokens.revokeAllForUser(token.getUserId(), now);
			audit.record(AuthEventType.TOKEN_REUSE_DETECTED, token.getUserId(), ip);
			return Optional.empty();
		}
		if (token.isExpiredAt(now)) {
			return Optional.empty();
		}
		Optional<User> user = users.findById(token.getUserId()).filter(User::isActive);
		if (user.isEmpty()) {
			token.revoke(now);
			return Optional.empty();
		}
		Created next = create(token.getUserId(), now);
		token.rotateTo(next.token(), now);
		return Optional.of(new Rotation(user.get(), next.raw()));
	}

	/** Logout: revokes this token if it exists. Unknown tokens are ignored. */
	@Transactional
	public void revoke(String raw) {
		if (raw == null || raw.isBlank()) {
			return;
		}
		tokens.findByTokenHash(hash(raw)).ifPresent((token) -> token.revoke(clock.instant()));
	}

	/** Revokes every active refresh token of a user (deactivation, reuse detection). */
	@Transactional
	public void revokeAllFor(UUID userId) {
		tokens.revokeAllForUser(userId, clock.instant());
	}

	/** Finds the user a token belongs to, without changing anything. Used for audit on logout. */
	@Transactional(readOnly = true)
	public Optional<UUID> ownerOf(String raw) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		return tokens.findByTokenHash(hash(raw)).map(RefreshToken::getUserId);
	}

	private Created create(UUID userId, Instant now) {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		RefreshToken token = tokens.saveAndFlush(new RefreshToken(userId, hash(raw), now, now.plus(ttl)));
		return new Created(token, raw);
	}

	private record Created(RefreshToken token, String raw) {
	}

	static String hash(String raw) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.US_ASCII));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public record Rotation(User user, String refreshToken) {
	}

}
