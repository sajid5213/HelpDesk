import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'

import { listTickets } from '../api/endpoints'
import type { AssigneeFilter } from '../api/types'
import { useAuth, useCurrentUser } from '../auth/AuthContext'
import { personName } from '../lib/format'
import { Link, useRouter } from '../router'
import './AppShell.css'

/** Fired after any change to a ticket, so the counts in the sidebar stay true. */
export const TICKETS_CHANGED = 'helpdesk:tickets-changed'

export function notifyTicketsChanged(): void {
  window.dispatchEvent(new Event(TICKETS_CHANGED))
}

const VIEWS: { view: AssigneeFilter; label: string }[] = [
  { view: 'any', label: 'Open tickets' },
  { view: 'mine', label: 'Assigned to me' },
  { view: 'unassigned', label: 'Unassigned' },
]

type Counts = Partial<Record<AssigneeFilter, number>>

export function AppShell({ children }: { children: ReactNode }) {
  const user = useCurrentUser()
  const { signOut } = useAuth()
  const { path, search } = useRouter()
  const [counts, setCounts] = useState<Counts>({})

  useEffect(() => {
    let cancelled = false
    const load = () =>
      Promise.all(
        VIEWS.map((v) =>
          listTickets({ status: 'OPEN', assignee: v.view, size: 1 }).then((page) => [v.view, page.page.totalElements] as const),
        ),
      )
        .then((entries) => {
          if (!cancelled) {
            setCounts(Object.fromEntries(entries))
          }
        })
        .catch(() => {
          // Counts are a convenience; the list itself reports errors.
        })
    load()
    window.addEventListener(TICKETS_CHANGED, load)
    return () => {
      cancelled = true
      window.removeEventListener(TICKETS_CHANGED, load)
    }
  }, [path])

  const activeView = path === '/tickets' ? ((search.get('assignee') as AssigneeFilter | null) ?? 'any') : null

  return (
    <div className="shell">
      <aside className="shell-sidebar">
        <Link to="/tickets" className="shell-wordmark">
          HelpDesk
        </Link>

        <nav className="shell-nav" aria-label="Ticket views">
          {VIEWS.map((v) => (
            <Link
              key={v.view}
              to={v.view === 'any' ? '/tickets' : `/tickets?assignee=${v.view}`}
              className={`shell-nav-item${activeView === v.view ? ' is-active' : ''}`}
              aria-current={activeView === v.view ? 'page' : undefined}
            >
              <span>{v.label}</span>
              <span className="shell-count" aria-label={`${counts[v.view] ?? 0} open`}>
                {counts[v.view] ?? ''}
              </span>
            </Link>
          ))}
        </nav>

        <div className="shell-user">
          <p className="shell-user-name">{personName(user.email)}</p>
          <p className="shell-user-meta">
            {user.role === 'ADMIN' ? 'Admin' : 'Agent'}, {user.email}
          </p>
          <button className="btn btn-quiet shell-signout" type="button" onClick={() => void signOut()}>
            Sign out
          </button>
        </div>
      </aside>

      <main className="shell-main">{children}</main>
    </div>
  )
}
