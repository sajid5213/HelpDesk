import { useEffect, useState } from 'react'

import { ApiError } from '../api/client'
import { listTickets } from '../api/endpoints'
import type { AssigneeFilter, Page, TicketCategory, TicketStatus, TicketSummary } from '../api/types'
import { CategoryLabel, StatusBadge } from '../components/TicketBits'
import { age, CATEGORY_LABEL, personName } from '../lib/format'
import { Link, useRouter } from '../router'
import './TicketListPage.css'

const PAGE_SIZE = 20

const TITLES: Record<AssigneeFilter, string> = {
  any: 'Open tickets',
  mine: 'Assigned to me',
  unassigned: 'Unassigned',
}

const STATUS_FILTERS: { value: TicketStatus | 'ALL'; label: string }[] = [
  { value: 'OPEN', label: 'Open' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'CLOSED', label: 'Closed' },
  { value: 'ALL', label: 'All' },
]

function emptyMessage(view: AssigneeFilter, filtered: boolean): string {
  if (filtered) {
    return 'No tickets match these filters.'
  }
  switch (view) {
    case 'mine':
      return 'Nothing assigned to you. Claim a ticket from Unassigned to start.'
    case 'unassigned':
      return 'Every ticket has an owner.'
    default:
      return 'Nothing waiting. New student emails appear here.'
  }
}

export function TicketListPage() {
  const { search, navigate } = useRouter()
  const view = (search.get('assignee') as AssigneeFilter | null) ?? 'any'
  const status = (search.get('status') as TicketStatus | 'ALL' | null) ?? 'OPEN'
  const category = search.get('category') ?? ''
  const q = search.get('q') ?? ''
  const page = Number(search.get('page') ?? '0')

  const [draftQuery, setDraftQuery] = useState(q)
  const [result, setResult] = useState<Page<TicketSummary> | null>(null)
  const [error, setError] = useState<string | null>(null)

  const title = status === 'OPEN' ? TITLES[view] : view === 'any' ? 'All tickets' : TITLES[view]

  /** Filters live in the address bar, so every view can be bookmarked and shared. */
  function setFilter(changes: Record<string, string | null>) {
    const next = new URLSearchParams(search)
    for (const [key, value] of Object.entries(changes)) {
      if (value === null || value === '') {
        next.delete(key)
      } else {
        next.set(key, value)
      }
    }
    if (!('page' in changes)) {
      next.delete('page')
    }
    const query = next.toString()
    navigate(query ? `/tickets?${query}` : '/tickets', { replace: true })
  }

  useEffect(() => setDraftQuery(q), [q])

  useEffect(() => {
    if (draftQuery === q) {
      return
    }
    const timer = window.setTimeout(() => setFilter({ q: draftQuery.trim() || null }), 300)
    return () => window.clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draftQuery])

  useEffect(() => {
    let cancelled = false
    setError(null)
    listTickets({
      assignee: view,
      status: status === 'ALL' ? undefined : status,
      category: (category || undefined) as TicketCategory | 'UNCLASSIFIED' | undefined,
      q: q || undefined,
      page,
      size: PAGE_SIZE,
      sort: 'updatedAt,desc',
    })
      .then((data) => !cancelled && setResult(data))
      .catch((caught) => {
        if (!cancelled) {
          setError(caught instanceof ApiError ? caught.message : 'The ticket list could not be loaded.')
        }
      })
    return () => {
      cancelled = true
    }
  }, [view, status, category, q, page])

  const total = result?.page.totalElements ?? 0
  const first = total === 0 ? 0 : page * PAGE_SIZE + 1
  const last = Math.min(total, (page + 1) * PAGE_SIZE)
  const filtered = Boolean(category || q || status !== 'OPEN')

  return (
    <div className="queue">
      <header className="queue-header">
        <h1 className="queue-title">{title}</h1>
        <p className="queue-total" aria-live="polite">
          {result ? `${total} ${total === 1 ? 'ticket' : 'tickets'}` : ''}
        </p>
      </header>

      <div className="queue-filters">
        <div className="segmented" role="group" aria-label="Status">
          {STATUS_FILTERS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={`segmented-item${status === option.value ? ' is-active' : ''}`}
              aria-pressed={status === option.value}
              onClick={() => setFilter({ status: option.value === 'OPEN' ? null : option.value })}
            >
              {option.label}
            </button>
          ))}
        </div>

        <label className="queue-select">
          <span className="visually-hidden">Category</span>
          <select className="control" value={category} onChange={(event) => setFilter({ category: event.target.value })}>
            <option value="">All categories</option>
            {(Object.keys(CATEGORY_LABEL) as TicketCategory[]).map((key) => (
              <option key={key} value={key}>
                {CATEGORY_LABEL[key]}
              </option>
            ))}
            <option value="UNCLASSIFIED">Not sorted yet</option>
          </select>
        </label>

        <label className="queue-search">
          <span className="visually-hidden">Search by subject or student email</span>
          <input
            className="control"
            type="search"
            placeholder="Search subject or student email"
            value={draftQuery}
            onChange={(event) => setDraftQuery(event.target.value)}
          />
        </label>
      </div>

      {error && (
        <p className="notice notice-error" role="alert">
          {error}
        </p>
      )}

      <div className="queue-table-wrap">
        <table className="queue-table">
          <thead>
            <tr>
              <th scope="col">Ticket</th>
              <th scope="col">Category</th>
              <th scope="col">Status</th>
              <th scope="col">Owner</th>
              <th scope="col" className="queue-col-age">
                Updated
              </th>
            </tr>
          </thead>
          <tbody>
            {result?.content.map((ticket) => (
              <tr key={ticket.id} className="queue-row" onClick={() => navigate(`/tickets/${ticket.id}`)}>
                <td className="queue-cell-ticket">
                  <Link to={`/tickets/${ticket.id}`} className="queue-subject" onClick={(e) => e.stopPropagation()}>
                    {ticket.subject}
                  </Link>
                  <span className="queue-student">{ticket.studentName ?? ticket.studentEmail}</span>
                  {ticket.aiStatus === 'PENDING' && <span className="queue-ai">AI is reading</span>}
                </td>
                <td>
                  <CategoryLabel category={ticket.category} />
                </td>
                <td>
                  <StatusBadge status={ticket.status} />
                </td>
                <td className={ticket.assigneeEmail ? 'queue-owner' : 'queue-owner is-none'}>
                  {ticket.assigneeEmail ? personName(ticket.assigneeEmail) : 'No one yet'}
                </td>
                <td className="queue-col-age">
                  <time dateTime={ticket.updatedAt} title={new Date(ticket.updatedAt).toLocaleString()}>
                    {age(ticket.updatedAt)}
                  </time>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {result && result.content.length === 0 && <p className="queue-empty">{emptyMessage(view, filtered)}</p>}
        {!result && !error && <p className="queue-empty">Loading tickets</p>}
      </div>

      {total > PAGE_SIZE && (
        <nav className="queue-pager" aria-label="Pages">
          <span>
            {first} to {last} of {total}
          </span>
          <button
            className="btn btn-secondary"
            type="button"
            disabled={page === 0}
            onClick={() => setFilter({ page: String(page - 1) })}
          >
            Previous
          </button>
          <button
            className="btn btn-secondary"
            type="button"
            disabled={last >= total}
            onClick={() => setFilter({ page: String(page + 1) })}
          >
            Next
          </button>
        </nav>
      )}
    </div>
  )
}
