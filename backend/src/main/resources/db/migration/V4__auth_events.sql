-- Audit trail for auth. Never stores passwords or tokens.
create table auth_events (
  id bigserial primary key,
  event_type text not null,
  user_id uuid references users (id),
  ip text,
  created_at timestamptz not null
);

create index auth_events_user_idx on auth_events (user_id, created_at);
