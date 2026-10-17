package com.helpdesk.user;

import java.util.UUID;

/**
 * Published when a user's existing sessions must end: deactivation, password change, or role
 * change. The auth package listens and revokes the user's refresh tokens in the same transaction.
 */
public record UserSessionsInvalidated(UUID userId) {
}
