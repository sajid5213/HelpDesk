-- Where the AI is with a ticket: waiting, finished, failed after retries, or not run
-- (AI turned off, or Claude declined the request). FAILED and SKIPPED tickets go to an agent.
alter table tickets add column ai_status text not null default 'PENDING'
  check (ai_status in ('PENDING', 'DONE', 'FAILED', 'SKIPPED'));
alter table tickets add column ai_processed_at timestamptz;
