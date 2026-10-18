import { describe, expect, it } from 'vitest'

import { ApiError } from '../api/client'
import { signInError } from './LoginPage'

describe('signInError', () => {
  it('explains wrong credentials without saying which part was wrong', () => {
    expect(signInError(new ApiError(401, 'x', 'invalid_credentials'))).toBe('That email or password is incorrect.')
  })

  it('explains a locked account', () => {
    expect(signInError(new ApiError(401, 'x', 'account_locked'))).toContain('locked for a few minutes')
  })

  it('tells the user how long to wait when rate limited', () => {
    expect(signInError(new ApiError(429, 'x', 'rate_limited', {}, 42))).toBe('Too many attempts. Try again in 42 seconds.')
  })

  it('says when the server cannot be reached', () => {
    expect(signInError(new TypeError('Failed to fetch'))).toContain('can’t be reached')
  })
})
