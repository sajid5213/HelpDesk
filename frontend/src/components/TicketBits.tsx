import type { TicketCategory, TicketStatus } from '../api/types'
import { CATEGORY_LABEL, STATUS_LABEL } from '../lib/format'
import './TicketBits.css'

export function StatusBadge({ status }: { status: TicketStatus }) {
  return <span className={`status status-${status.toLowerCase()}`}>{STATUS_LABEL[status]}</span>
}

export function CategoryLabel({ category }: { category: TicketCategory | null }) {
  if (!category) {
    return <span className="category category-none">Not sorted yet</span>
  }
  return <span className={`category category-${category.toLowerCase()}`}>{CATEGORY_LABEL[category]}</span>
}
