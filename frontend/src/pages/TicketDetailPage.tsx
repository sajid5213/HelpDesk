import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'

import { ApiError } from '../api/client'
import { addNote, claimTicket, getTicket, sendReply, updateTicket } from '../api/endpoints'
import type { MessageView, TicketCategory, TicketDetail, TicketStatus } from '../api/types'
import { useCurrentUser } from '../auth/AuthContext'
import { notifyTicketsChanged } from '../components/AppShell'
import { CategoryLabel, StatusBadge } from '../components/TicketBits'
import { useToast } from '../components/Toast'
import { AI_STATUS_LABEL, CATEGORY_LABEL, personName, when } from '../lib/format'
import { Link } from '../router'
import './TicketDetailPage.css'

function loadError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'This ticket belongs to another agent.'
    }
    if (error.status === 404) {
      return 'This ticket doesn’t exist.'
    }
    return error.message
  }
  return 'The ticket could not be loaded.'
}

function actionError(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'already_assigned':
        return 'Another agent claimed this ticket first.'
      case 'ticket_closed':
        return 'This ticket is closed, so it can’t be changed.'
      case 'conflict':
        return 'Someone else changed this ticket. It has been reloaded; check it and try again.'
      default:
        return error.status === 403 ? 'Claim this ticket before working on it.' : error.message
    }
  }
  return 'That didn’t work. Check your connection and try again.'
}

export function TicketDetailPage({ id }: { id: number }) {
  const user = useCurrentUser()
  const toast = useToast()
  const [ticket, setTicket] = useState<TicketDetail | null>(null)
  const [loadProblem, setLoadProblem] = useState<string | null>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const reload = useCallback(
    () =>
      getTicket(id)
        .then((data) => {
          setTicket(data)
          setLoadProblem(null)
        })
        .catch((caught) => setLoadProblem(loadError(caught))),
    [id],
  )

  useEffect(() => {
    void reload()
  }, [reload])

  // While the AI is still reading the email, check back every few seconds.
  useEffect(() => {
    if (ticket?.aiStatus !== 'PENDING') {
      return
    }
    const timer = window.setTimeout(() => void reload(), 3000)
    return () => window.clearTimeout(timer)
  }, [ticket, reload])

  async function act(action: () => Promise<unknown>, done: string) {
    setBusy(true)
    setProblem(null)
    try {
      const result = await action()
      if (result && typeof result === 'object' && 'messages' in result) {
        setTicket(result as TicketDetail)
      } else {
        await reload()
      }
      toast(done)
      notifyTicketsChanged()
      return true
    } catch (caught) {
      setProblem(actionError(caught))
      if (caught instanceof ApiError && caught.code === 'conflict') {
        await reload()
      }
      return false
    } finally {
      setBusy(false)
    }
  }

  if (loadProblem) {
    return (
      <div className="ticket-missing">
        <p className="notice notice-info">{loadProblem}</p>
        <Link to="/tickets" className="ticket-back">
          Back to tickets
        </Link>
      </div>
    )
  }

  if (!ticket) {
    return <p className="ticket-loading">Loading ticket</p>
  }

  const mine = ticket.assigneeId === user.id
  const canWork = (user.role === 'ADMIN' || mine) && ticket.status !== 'CLOSED'
  const canClaim = !ticket.assigneeId && ticket.status !== 'CLOSED'

  return (
    <article className="ticket">
      <header className="ticket-header">
        <Link to="/tickets" className="ticket-back">
          Back to tickets
        </Link>
        <h1 className="ticket-subject">{ticket.subject}</h1>
        <span className="ticket-rule" aria-hidden="true" />
        <p className="ticket-from">
          From <strong>{ticket.studentName ?? ticket.studentEmail}</strong>
          {ticket.studentName && <span className="ticket-email"> {ticket.studentEmail}</span>}
        </p>
      </header>

      <div className="ticket-layout">
        <div className="ticket-main">
          {problem && (
            <p className="notice notice-error" role="alert">
              {problem}
            </p>
          )}

          <ol className="thread" aria-label="Email thread">
            {ticket.messages.map((message) => (
              <ThreadMessage key={message.id} message={message} student={ticket.studentName ?? ticket.studentEmail} />
            ))}
          </ol>

          {canWork ? (
            <ReplyComposer
              key={ticket.id}
              suggestedReply={ticket.suggestedReply}
              busy={busy}
              onSend={(body) => act(() => sendReply(ticket.id, body), 'Reply sent')}
            />
          ) : (
            <p className="ticket-readonly">
              {ticket.status === 'CLOSED'
                ? 'This ticket is closed. If the student writes again, a new ticket opens.'
                : 'Claim this ticket to reply to the student.'}
            </p>
          )}
        </div>

        <aside className="ticket-rail" aria-label="Ticket details">
          <section className="rail-panel">
            <dl className="facts">
              <div>
                <dt>Status</dt>
                <dd>
                  <StatusBadge status={ticket.status} />
                </dd>
              </div>
              <div>
                <dt>Owner</dt>
                <dd>{ticket.assigneeEmail ? (mine ? 'You' : personName(ticket.assigneeEmail)) : 'No one yet'}</dd>
              </div>
              <div>
                <dt>Category</dt>
                <dd>
                  {canWork ? (
                    <CategorySelect
                      value={ticket.category}
                      disabled={busy}
                      onChange={(category) => act(() => updateTicket(ticket.id, { category }), 'Category updated')}
                    />
                  ) : (
                    <CategoryLabel category={ticket.category} />
                  )}
                </dd>
              </div>
              <div>
                <dt>Ticket</dt>
                <dd>#{ticket.id}</dd>
              </div>
              <div>
                <dt>Received</dt>
                <dd>{when(ticket.createdAt)}</dd>
              </div>
            </dl>

            <div className="rail-actions">
              {canClaim && (
                <button
                  className="btn btn-primary"
                  type="button"
                  disabled={busy}
                  onClick={() => void act(() => claimTicket(ticket.id), 'Ticket claimed')}
                >
                  Claim ticket
                </button>
              )}
              {canWork && <StatusActions status={ticket.status} busy={busy} onChange={(status, done) => act(() => updateTicket(ticket.id, { status }), done)} />}
            </div>
          </section>

          <AiSummary ticket={ticket} />

          <NotesPanel
            notes={ticket.notes}
            userId={user.id}
            canAdd={canWork}
            busy={busy}
            onAdd={(body) => act(() => addNote(ticket.id, body), 'Note added')}
          />
        </aside>
      </div>
    </article>
  )
}

function ThreadMessage({ message, student }: { message: MessageView; student: string }) {
  const fromStudent = message.direction === 'INBOUND'
  const author = fromStudent ? student : message.sentByAi ? 'Sent by AI' : personName(message.authorEmail)
  const kind = fromStudent ? 'is-student' : message.sentByAi ? 'is-ai' : 'is-staff'
  return (
    <li className={`message ${kind}`}>
      <p className="message-meta">
        <span className="message-author">{author}</span>
        <time dateTime={message.createdAt}>{when(message.createdAt)}</time>
      </p>
      <p className="message-body">{message.body}</p>
    </li>
  )
}

function ReplyComposer({
  suggestedReply,
  busy,
  onSend,
}: {
  suggestedReply: string | null
  busy: boolean
  onSend: (body: string) => Promise<boolean>
}) {
  const [body, setBody] = useState('')
  const usingDraft = suggestedReply !== null && body === suggestedReply

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (await onSend(body.trim())) {
      setBody('')
    }
  }

  return (
    <form className="composer" onSubmit={submit}>
      <div className="composer-head">
        <label htmlFor="reply" className="composer-label">
          Reply to the student
        </label>
        {suggestedReply && (
          <button className="btn btn-ai" type="button" onClick={() => setBody(suggestedReply)} disabled={busy || usingDraft}>
            Use AI draft
          </button>
        )}
      </div>
      <textarea
        id="reply"
        className={`control composer-text${usingDraft ? ' is-ai-draft' : ''}`}
        rows={8}
        value={body}
        onChange={(event) => setBody(event.target.value)}
        placeholder="Write your reply. The student receives it by email."
      />
      {usingDraft && <p className="composer-note">Drafted by AI. Read it before sending; edit anything that isn’t right.</p>}
      <div className="composer-foot">
        <button className="btn btn-primary" type="submit" disabled={busy || body.trim() === ''}>
          Send reply
        </button>
        <span className="composer-hint">Sending resolves the ticket.</span>
      </div>
    </form>
  )
}

function StatusActions({
  status,
  busy,
  onChange,
}: {
  status: TicketStatus
  busy: boolean
  onChange: (status: TicketStatus, done: string) => Promise<boolean>
}) {
  return (
    <div className="status-actions">
      {status === 'OPEN' && (
        <button className="btn btn-secondary" type="button" disabled={busy} onClick={() => void onChange('RESOLVED', 'Marked resolved')}>
          Mark resolved
        </button>
      )}
      {status === 'RESOLVED' && (
        <button className="btn btn-secondary" type="button" disabled={busy} onClick={() => void onChange('OPEN', 'Ticket reopened')}>
          Reopen
        </button>
      )}
      <button className="btn btn-quiet" type="button" disabled={busy} onClick={() => void onChange('CLOSED', 'Ticket closed')}>
        Close ticket
      </button>
    </div>
  )
}

function CategorySelect({
  value,
  disabled,
  onChange,
}: {
  value: TicketCategory | null
  disabled: boolean
  onChange: (category: TicketCategory) => void
}) {
  return (
    <select
      className="control facts-select"
      value={value ?? ''}
      disabled={disabled}
      aria-label="Category"
      onChange={(event) => onChange(event.target.value as TicketCategory)}
    >
      {!value && <option value="">Not sorted yet</option>}
      {(Object.keys(CATEGORY_LABEL) as TicketCategory[]).map((key) => (
        <option key={key} value={key}>
          {CATEGORY_LABEL[key]}
        </option>
      ))}
    </select>
  )
}

function AiSummary({ ticket }: { ticket: TicketDetail }) {
  return (
    <section className="rail-panel ai-note" aria-label="AI summary">
      <h2 className="rail-heading ai-heading">AI summary</h2>
      {ticket.aiStatus === 'DONE' && ticket.aiSummary ? (
        <p className="ai-text">{ticket.aiSummary}</p>
      ) : (
        <p className="ai-text is-quiet">
          {ticket.aiStatus === 'PENDING' ? `${AI_STATUS_LABEL.PENDING}…` : ticket.aiStatus === 'FAILED'
            ? 'The AI couldn’t read this email. Answer it yourself.'
            : 'The AI wasn’t used for this ticket.'}
        </p>
      )}
      {ticket.autoResolved && <p className="ai-flag">The AI answered this student itself.</p>}
    </section>
  )
}

function NotesPanel({
  notes,
  userId,
  canAdd,
  busy,
  onAdd,
}: {
  notes: TicketDetail['notes']
  userId: string
  canAdd: boolean
  busy: boolean
  onAdd: (body: string) => Promise<boolean>
}) {
  const [body, setBody] = useState('')

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (await onAdd(body.trim())) {
      setBody('')
    }
  }

  return (
    <section className="rail-panel" aria-label="Internal notes">
      <h2 className="rail-heading">Internal notes</h2>
      <p className="rail-sub">Only staff see these.</p>
      {notes.length > 0 && (
        <ul className="notes">
          {notes.map((note) => (
            <li key={note.id} className="note">
              <p className="note-body">{note.body}</p>
              <p className="note-meta">
                {note.authorUserId === userId ? 'You' : personName(note.authorEmail)}, {when(note.createdAt)}
              </p>
            </li>
          ))}
        </ul>
      )}
      {canAdd && (
        <form className="note-form" onSubmit={submit}>
          <label htmlFor="note" className="visually-hidden">
            Add an internal note
          </label>
          <textarea
            id="note"
            className="control"
            rows={3}
            value={body}
            onChange={(event) => setBody(event.target.value)}
            placeholder="Add a note for the team"
          />
          <button className="btn btn-secondary" type="submit" disabled={busy || body.trim() === ''}>
            Add note
          </button>
        </form>
      )}
    </section>
  )
}
