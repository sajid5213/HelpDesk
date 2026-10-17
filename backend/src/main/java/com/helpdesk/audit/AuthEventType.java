package com.helpdesk.audit;

public enum AuthEventType {

	// Self-service and system events
	LOGIN_SUCCESS, LOGIN_FAILURE, TOKEN_REFRESH, TOKEN_REUSE_DETECTED, LOGOUT, KEY_ROTATED,

	// Admin actions on accounts (actor_id is the admin)
	USER_CREATED, USER_UPDATED, USER_DEACTIVATED, USER_REACTIVATED, PASSWORD_CHANGED, ROLE_CHANGED

}
