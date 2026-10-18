import { useState } from 'react'
import type { FormEvent } from 'react'

import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import './LoginPage.css'

export function signInError(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'invalid_credentials':
        return 'That email or password is incorrect.'
      case 'account_locked':
        return 'Too many attempts. This account is locked for a few minutes.'
      case 'rate_limited':
        return error.retryAfterSeconds
          ? `Too many attempts. Try again in ${error.retryAfterSeconds} seconds.`
          : 'Too many attempts. Try again in a minute.'
      default:
        return error.status >= 500 ? 'The HelpDesk server had a problem. Try again shortly.' : error.message
    }
  }
  return 'The HelpDesk server can’t be reached. Check that it is running.'
}

export function LoginPage() {
  const { signIn } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await signIn(email.trim(), password)
    } catch (caught) {
      setError(signInError(caught))
      setBusy(false)
    }
  }

  return (
    <main className="login">
      <section className="login-brand" aria-hidden="true">
        <p className="login-wordmark">HelpDesk</p>
        <div className="login-statement">
          <p className="login-line">Every student, answered well.</p>
          <span className="login-rule" />
          <p className="login-sub">The support desk for your coding courses.</p>
        </div>
      </section>

      <section className="login-panel">
        <form className="login-form" onSubmit={submit} noValidate>
          <h1 className="login-title">Sign in</h1>
          <p className="login-hint">Use the account your admin created for you.</p>

          {error && (
            <p className="notice notice-error" role="alert">
              {error}
            </p>
          )}

          <label className="field">
            <span className="field-label">Email</span>
            <input
              className="control"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
              autoFocus
            />
          </label>

          <label className="field">
            <span className="field-label">Password</span>
            <input
              className="control"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
          </label>

          <button className="btn btn-primary login-submit" type="submit" disabled={busy || !email || !password}>
            {busy ? 'Signing in' : 'Sign in'}
          </button>
        </form>
      </section>
    </main>
  )
}
