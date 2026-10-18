/**
 * The access token lives only in this module's memory. It is never written to localStorage,
 * sessionStorage, or a readable cookie, so a script injected into the page cannot steal a token
 * that outlives the tab. Reloading the page restores the session from the HttpOnly refresh cookie.
 */
let accessToken: string | null = null

export function getToken(): string | null {
  return accessToken
}

export function setToken(token: string): void {
  accessToken = token
}

export function clearToken(): void {
  accessToken = null
}
