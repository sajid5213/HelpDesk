-- Opaque refresh tokens, stored only as SHA-256 hex. Rotated on every use.
create table refresh_tokens (
  id uuid primary key,
  user_id uuid not null references users (id),
  token_hash text not null unique check (char_length(token_hash) = 64),
  expires_at timestamptz not null,
  revoked_at timestamptz,
  -- Set when this token was rotated; presenting it again is reuse.
  replaced_by_id uuid references refresh_tokens (id),
  created_at timestamptz not null
);

create index refresh_tokens_user_idx on refresh_tokens (user_id) where revoked_at is null;
