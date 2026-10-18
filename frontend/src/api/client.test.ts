import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { getToken, setToken } from '../auth/tokenStore'
import { apiFetch, login, setSessionExpiredHandler } from './client'

type Handler = (url: string, init: RequestInit) => Response | Promise<Response>

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

let calls: { url: string; init: RequestInit }[]

function mockFetch(handler: Handler) {
  calls = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init: RequestInit = {}) => {
      calls.push({ url, init })
      return handler(url, init)
    }),
  )
}

function headerOf(init: RequestInit, name: string): string | null {
  return new Headers(init.headers).get(name)
}

describe('apiFetch', () => {
  beforeEach(() => {
    setToken('old-token')
    setSessionExpiredHandler(() => {})
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('never stores the token in web storage', async () => {
    mockFetch(() => json(200, { accessToken: 'fresh-token' }))
    const setItem = vi.spyOn(Storage.prototype, 'setItem')

    await login('agent@example.com', 'correct-horse-battery')

    expect(getToken()).toBe('fresh-token')
    expect(setItem).not.toHaveBeenCalled()
    expect(window.localStorage.length).toBe(0)
    expect(window.sessionStorage.length).toBe(0)
  })

  it('sends the browser marker, credentials, and bearer token on every call', async () => {
    mockFetch(() => json(200, { ok: true }))

    await apiFetch('/api/tickets')

    const { init } = calls[0]
    expect(headerOf(init, 'X-Auth-Client')).toBe('browser')
    expect(headerOf(init, 'Authorization')).toBe('Bearer old-token')
    expect(init.credentials).toBe('include')
  })

  it('shares one refresh between parallel requests that hit 401', async () => {
    let refreshes = 0
    mockFetch(async (url, init) => {
      if (url === '/auth/refresh') {
        refreshes++
        await new Promise((resolve) => setTimeout(resolve, 20))
        return json(200, { accessToken: 'new-token' })
      }
      return headerOf(init, 'Authorization') === 'Bearer new-token' ? json(200, { url }) : json(401, {})
    })

    const results = await Promise.all([apiFetch('/api/a'), apiFetch('/api/b'), apiFetch('/api/c')])

    expect(refreshes).toBe(1)
    expect(results).toEqual([{ url: '/api/a' }, { url: '/api/b' }, { url: '/api/c' }])
    expect(getToken()).toBe('new-token')
  })

  it('signs out when the refresh fails', async () => {
    const expired = vi.fn()
    setSessionExpiredHandler(expired)
    mockFetch((url) => (url === '/auth/refresh' ? json(401, { error: 'invalid_refresh_token' }) : json(401, {})))

    await expect(apiFetch('/api/tickets')).rejects.toMatchObject({ status: 401 })

    expect(expired).toHaveBeenCalledOnce()
    expect(getToken()).toBeNull()
  })

  it('does not loop when the retried request is rejected again', async () => {
    const expired = vi.fn()
    setSessionExpiredHandler(expired)
    mockFetch((url) => (url === '/auth/refresh' ? json(200, { accessToken: 'new-token' }) : json(401, {})))

    await expect(apiFetch('/api/tickets')).rejects.toMatchObject({ status: 401 })

    expect(calls.map((c) => c.url)).toEqual(['/api/tickets', '/auth/refresh', '/api/tickets'])
    expect(expired).toHaveBeenCalledOnce()
  })

  it('turns problem+json into an error with its code and field messages', async () => {
    mockFetch(() =>
      json(400, { detail: 'Some fields are missing or invalid.', errors: { password: 'must not be blank' } }),
    )

    await expect(apiFetch('/api/users', { method: 'POST', body: '{}' })).rejects.toMatchObject({
      status: 400,
      message: 'Some fields are missing or invalid.',
      fields: { password: 'must not be blank' },
    })
  })
})
