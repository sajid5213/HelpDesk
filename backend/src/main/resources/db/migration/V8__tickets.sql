-- Support tickets, their email thread, and internal notes.
create table tickets (
  id bigserial primary key,
  subject text not null,
  student_email text not null check (student_email = lower(student_email)),
  student_name text,
  status text not null check (status in ('OPEN', 'RESOLVED', 'CLOSED')),
  -- Null until the AI (Phase 5) or an agent sets it.
  category text check (category in ('GENERAL_QUESTION', 'TECHNICAL_QUESTION', 'REFUND_REQUEST')),
  category_set_manually boolean not null default false,
  ai_summary text,
  suggested_reply text,
  auto_resolved boolean not null default false,
  assignee_id uuid references users (id),
  created_at timestamptz not null,
  updated_at timestamptz not null,
  first_response_at timestamptz,
  resolved_at timestamptz,
  closed_at timestamptz,
  version int not null default 0
);

create index tickets_status_idx on tickets (status);
create index tickets_assignee_idx on tickets (assignee_id);
create index tickets_created_idx on tickets (created_at);
create index tickets_updated_idx on tickets (updated_at);

create table ticket_messages (
  id bigserial primary key,
  ticket_id bigint not null references tickets (id),
  direction text not null check (direction in ('INBOUND', 'OUTBOUND')),
  -- Null for messages from the student.
  author_user_id uuid references users (id),
  body text not null,
  sent_by_ai boolean not null default false,
  created_at timestamptz not null
);

create index ticket_messages_ticket_idx on ticket_messages (ticket_id, id);

-- Internal notes between staff. Never emailed to the student.
create table ticket_notes (
  id bigserial primary key,
  ticket_id bigint not null references tickets (id),
  author_user_id uuid not null references users (id),
  body text not null,
  created_at timestamptz not null
);

create index ticket_notes_ticket_idx on ticket_notes (ticket_id, id);
