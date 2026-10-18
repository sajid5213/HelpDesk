import { clearToken, getToken, setToken } from '../auth/tokenStore'

/** An error answer from the API (RFC 9457 problem+json), with its stable error code. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string | undefined
  readonly fields: Record<string, string>
  readonly retryAfterSeconds: number | undefined

  constructor(
    status: number,
    message: string,
    code?: string,
    fields: Record<string, string> = {},
    retryAfterSeconds?: number,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fields = fields
    this.retryAfterSeconds = retryAfterSeconds
  }
}

type SessionListener = () => void

let onSessionExpired: SessionListener = () => {}

/** Called when the session cannot be renewed: the app goes back to the sign-in page. */
export function setSessionExpiredHandler(listener: SessionListener): void {
  onSessionExpired = listener
}

/** The one refresh in flight, shared by every request that hit a 401 at the same time. */
let refreshing: Promise<boolean> | null = null

/**
 * Exchanges the HttpOnly refresh cookie for a new access token. Concurrent callers share one
 * request: the backend rotates the refresh token on every use and treats a second use of the old
 * one as theft, so parallel refreshes would sign the user out everywhere.
 */
export function refreshSession(): Promise<boolean> {
  if (!refreshing) {
    refreshing = (async () => {
      try {
        const response = await fetch('/auth/refresh', {
          method: 'POST',
          headers: { 'X-Auth-Client': 'browser' },
          credentials: 'include',
        })
        if (!response.ok) {
          clearToken()
          return false
        }
        const body = (await response.json()) as { accessToken: string }
        setToken(body.accessToken)
        return true
      } catch {
        clearToken()
        return false
      } finally {
        refreshing = null
      }
    })()
  }
  return refreshing
}

function send(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers)
  // Tells the backend to keep the refresh token in the HttpOnly cookie, never in a body.
  headers.set('X-Auth-Client', 'browser')
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  return fetch(path, { ...init, headers, credentials: 'include' })
}

async function toError(response: Response): Promise<ApiError> {
  let body: { detail?: string; title?: string; error?: string; errors?: Record<string, string> } = {}
  try {
    body = await response.json()
  } catch {
    // Not JSON: keep the generic message below.
  }
  const retryAfter = Number(response.headers.get('Retry-After'))
  return new ApiError(
    response.status,
    body.detail ?? body.title ?? `Request failed (${response.status})`,
    body.error,
    body.errors ?? {},
    Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : undefined,
  )
}

/**
 * Calls the API as the signed-in user. A 401 triggers one shared refresh and one retry; if that
 * fails too, the session has ended and the app returns to the sign-in page.
 */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response = await send(path, init)
  if (response.status === 401 && !path.startsWith('/auth/')) {
    const renewed = await refreshSession()
    if (renewed) {
      response = await send(path, init)
    }
    if (!renewed || response.status === 401) {
      clearToken()
      onSessionExpired()
      throw await toError(response)
    }
  }
  if (!response.ok) {
    throw await toError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

/** Signs in with email and password; the refresh token arrives as an HttpOnly cookie. */
export async function login(email: string, password: string): Promise<void> {
  const response = await send('/auth/token', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
  if (!response.ok) {
    throw await toError(response)
  }
  const body = (await response.json()) as { accessToken: string }
  setToken(body.accessToken)
}

/** Revokes the refresh token and clears the cookie. Never fails from the user's point of view. */
export async function logout(): Promise<void> {
  try {
    await send('/auth/logout', { method: 'POST' })
  } finally {
    clearToken()
  }
}
