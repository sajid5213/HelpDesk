create table users (
  id uuid primary key,
  -- Stored lowercased (normalized in User.normalizeEmail), so lookups and uniqueness ignore case.
  email text not null unique check (email = lower(email)),
  password_hash text not null,
  role text not null check (role in ('ADMIN', 'AGENT')),
  active boolean not null default true,
  failed_login_count int not null default 0,
  locked_until timestamptz,
  created_at timestamptz not null
);
