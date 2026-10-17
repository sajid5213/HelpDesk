package com.helpdesk.keys;

public enum KeyStatus {

	/** Signs new tokens. Exactly one at a time. */
	ACTIVE,

	/** No longer signs; still published in JWKS until tokens it signed have expired. */
	RETIRED

}
