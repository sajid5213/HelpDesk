-- FAQ articles and policies. The AI (Phase 5) answers only from these.
create table kb_articles (
  id bigserial primary key,
  title text not null,
  body text not null,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
