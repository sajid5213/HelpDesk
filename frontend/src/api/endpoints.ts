import { apiFetch } from './client'
import type { Me, Page, TicketCategory, TicketDetail, TicketQuery, TicketStatus, TicketSummary } from './types'

export function fetchMe(): Promise<Me> {
  return apiFetch<Me>('/api/me')
}

export function listTickets(query: TicketQuery): Promise<Page<TicketSummary>> {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== '') {
      params.set(key, String(value))
    }
  }
  return apiFetch<Page<TicketSummary>>(`/api/tickets?${params}`)
}

export function getTicket(id: number): Promise<TicketDetail> {
  return apiFetch<TicketDetail>(`/api/tickets/${id}`)
}

export function claimTicket(id: number): Promise<TicketSummary> {
  return apiFetch<TicketSummary>(`/api/tickets/${id}/claim`, { method: 'POST' })
}

export function sendReply(id: number, body: string): Promise<TicketDetail> {
  return apiFetch<TicketDetail>(`/api/tickets/${id}/replies`, { method: 'POST', body: JSON.stringify({ body }) })
}

export function addNote(id: number, body: string): Promise<TicketDetail> {
  return apiFetch<TicketDetail>(`/api/tickets/${id}/notes`, { method: 'POST', body: JSON.stringify({ body }) })
}

export function updateTicket(
  id: number,
  change: { status?: TicketStatus; category?: TicketCategory },
): Promise<TicketDetail> {
  return apiFetch<TicketDetail>(`/api/tickets/${id}`, { method: 'PATCH', body: JSON.stringify(change) })
}
