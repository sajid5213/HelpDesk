import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

import { login, logout, refreshSession, setSessionExpiredHandler } from '../api/client'
import { fetchMe } from '../api/endpoints'
import type { Me } from '../api/types'

type AuthState = { status: 'checking' } | { status: 'signedOut' } | { status: 'signedIn'; user: Me }

interface AuthValue {
  state: AuthState
  signIn: (email: string, password: string) => Promise<void>
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'checking' })

  useEffect(() => {
    setSessionExpiredHandler(() => setState({ status: 'signedOut' }))
    let cancelled = false
    // A page reload loses the in-memory token; the HttpOnly cookie gets a new one.
    refreshSession()
      .then(async (renewed) => {
        if (!renewed) {
          return { status: 'signedOut' } as const
        }
        return { status: 'signedIn', user: await fetchMe() } as const
      })
      .catch(() => ({ status: 'signedOut' }) as const)
      .then((next) => {
        if (!cancelled) {
          setState(next)
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  const signIn = useCallback(async (email: string, password: string) => {
    await login(email, password)
    setState({ status: 'signedIn', user: await fetchMe() })
  }, [])

  const signOut = useCallback(async () => {
    await logout()
    setState({ status: 'signedOut' })
  }, [])

  const value = useMemo(() => ({ state, signIn, signOut }), [state, signIn, signOut])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthValue {
  const auth = useContext(AuthContext)
  if (!auth) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return auth
}

/** The signed-in user; only call inside screens that require sign-in. */
export function useCurrentUser(): Me {
  const { state } = useAuth()
  if (state.status !== 'signedIn') {
    throw new Error('Not signed in')
  }
  return state.user
}
