import type { AiStatus, TicketCategory, TicketStatus } from '../api/types'

export const STATUS_LABEL: Record<TicketStatus, string> = {
  OPEN: 'Open',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
}

export const CATEGORY_LABEL: Record<TicketCategory, string> = {
  GENERAL_QUESTION: 'General question',
  TECHNICAL_QUESTION: 'Technical question',
  REFUND_REQUEST: 'Refund request',
}

export const AI_STATUS_LABEL: Record<AiStatus, string> = {
  PENDING: 'AI is reading this email',
  DONE: 'Read by AI',
  FAILED: 'AI could not process this email',
  SKIPPED: 'Handled without AI',
}

/** "alice.smith@helpdesk.local" becomes "Alice Smith": staff are named, never shown as ids. */
export function personName(email: string | null | undefined): string {
  if (!email) {
    return 'Unknown'
  }
  const local = email.split('@')[0].replace(/\d+$/, '')
  const words = local.split(/[._-]+/).filter(Boolean)
  if (words.length === 0) {
    return email
  }
  return words.map((word) => word.charAt(0).toUpperCase() + word.slice(1)).join(' ')
}

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/** Short, scannable age for lists: "just now", "12 min", "3 h", "Oct 14". */
export function age(iso: string, now: number = Date.now()): string {
  const then = new Date(iso).getTime()
  const diff = Math.max(0, now - then)
  if (diff < MINUTE) {
    return 'just now'
  }
  if (diff < HOUR) {
    return `${Math.floor(diff / MINUTE)} min`
  }
  if (diff < DAY) {
    return `${Math.floor(diff / HOUR)} h`
  }
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

/** Full timestamp for the thread: "Oct 17, 2026, 3:42 PM". */
export function when(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}
