/** Shapes returned by the HelpDesk API (mirrors the backend records). */

export type Role = 'ADMIN' | 'AGENT'
export type TicketStatus = 'OPEN' | 'RESOLVED' | 'CLOSED'
export type TicketCategory = 'GENERAL_QUESTION' | 'TECHNICAL_QUESTION' | 'REFUND_REQUEST'
export type AiStatus = 'PENDING' | 'DONE' | 'FAILED' | 'SKIPPED'
export type AssigneeFilter = 'any' | 'mine' | 'unassigned'

export interface Me {
  id: string
  email: string
  role: Role
  active: boolean
  locked: boolean
  createdAt: string
}

export interface TicketSummary {
  id: number
  subject: string
  studentEmail: string
  studentName: string | null
  status: TicketStatus
  category: TicketCategory | null
  aiStatus: AiStatus
  assigneeId: string | null
  assigneeEmail: string | null
  createdAt: string
  updatedAt: string
}

export interface MessageView {
  id: number
  direction: 'INBOUND' | 'OUTBOUND'
  authorUserId: string | null
  authorEmail: string | null
  body: string
  sentByAi: boolean
  createdAt: string
}

export interface NoteView {
  id: number
  authorUserId: string
  authorEmail: string | null
  body: string
  createdAt: string
}

export interface TicketDetail extends TicketSummary {
  categorySetManually: boolean
  aiSummary: string | null
  suggestedReply: string | null
  autoResolved: boolean
  firstResponseAt: string | null
  resolvedAt: string | null
  closedAt: string | null
  messages: MessageView[]
  notes: NoteView[]
}

export interface Page<T> {
  content: T[]
  page: { size: number; number: number; totalElements: number; totalPages: number }
}

export interface TicketQuery {
  status?: TicketStatus
  category?: TicketCategory | 'UNCLASSIFIED'
  assignee?: AssigneeFilter
  q?: string
  page?: number
  size?: number
  sort?: string
}
