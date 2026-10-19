# 3. Data model (ER diagram)

[← 2. Deployment](../02-deployment/README.md) · [All diagrams](../README.md) · Next: [4. Sequence diagrams →](../04-sequence-diagrams/README.md)

Every table, from the Flyway migrations in `backend/src/main/resources/db/migration/` (`V1` to `V11`). Reading the migrations in order shows the tables arriving phase by phase.

## 3a. Accounts and security

```mermaid
erDiagram
    USERS ||--o{ REFRESH_TOKENS : "owns"
    REFRESH_TOKENS |o--o| REFRESH_TOKENS : "replaced_by_id (rotation)"
    USERS |o--o{ AUTH_EVENTS : "user_id: the account it is about"
    USERS |o--o{ AUTH_EVENTS : "actor_id: the admin who did it"

    USERS {
        uuid id PK
        text email UK "always lowercase"
        text password_hash "BCrypt"
        text role "ADMIN or AGENT"
        boolean active
        int failed_login_count "for lockout"
        timestamptz locked_until "null when not locked"
        timestamptz created_at
    }
    REFRESH_TOKENS {
        uuid id PK
        uuid user_id FK
        text token_hash UK "SHA-256, never the raw token"
        timestamptz expires_at
        timestamptz revoked_at "set on rotate or logout"
        uuid replaced_by_id FK "the token it was rotated to"
        timestamptz created_at
    }
    AUTH_EVENTS {
        bigserial id PK
        text event_type "LOGIN_SUCCESS, TOKEN_REUSE_DETECTED, ..."
        uuid user_id FK
        uuid actor_id FK
        text ip
        timestamptz created_at
    }
    SIGNING_KEYS {
        text kid PK "the kid in every JWT header"
        text public_jwk "published in JWKS"
        text private_key_encrypted "AES-256-GCM with HELPDESK_KEK"
        text status "ACTIVE or RETIRED, one ACTIVE at most"
        timestamptz created_at
        timestamptz retired_at
    }
```

- **No passwords or tokens in plain text.** Passwords are BCrypt hashes, refresh tokens are SHA-256 hashes, and private signing keys are encrypted with a key (`HELPDESK_KEK`) that is never stored in the database.
- **`signing_keys` stands alone.** It isn't linked to users: any user's token is signed by whichever key is ACTIVE. A partial unique index stops two keys being ACTIVE at once.
- **`auth_events` links to `users` twice:** who the event is about, and (for admin actions) who did it.

## 3b. Tickets and the knowledge base

```mermaid
erDiagram
    TICKETS ||--o{ TICKET_MESSAGES : "thread"
    TICKETS ||--o{ TICKET_NOTES : "internal notes"
    USERS |o--o{ TICKETS : "assignee_id"
    USERS |o--o{ TICKET_MESSAGES : "author_user_id (null = student or AI)"
    USERS ||--o{ TICKET_NOTES : "author_user_id"

    TICKETS {
        bigserial id PK "the number in Re: [#id]"
        text subject
        text student_email "lowercase"
        text student_name
        text status "OPEN, RESOLVED, CLOSED"
        text category "GENERAL, TECHNICAL, REFUND; null until set"
        boolean category_set_manually "AI never overwrites it"
        text ai_status "PENDING, DONE, FAILED, SKIPPED"
        text ai_summary
        text suggested_reply "AI draft for the agent"
        boolean auto_resolved "the AI answered alone"
        uuid assignee_id FK "null = in the shared queue"
        timestamptz first_response_at
        timestamptz resolved_at
        timestamptz closed_at
        int version "optimistic locking"
    }
    TICKET_MESSAGES {
        bigserial id PK
        bigint ticket_id FK
        text direction "INBOUND from student, OUTBOUND to student"
        uuid author_user_id FK
        text body
        boolean sent_by_ai
        timestamptz created_at
    }
    TICKET_NOTES {
        bigserial id PK
        bigint ticket_id FK
        uuid author_user_id FK
        text body "staff only, never emailed"
        timestamptz created_at
    }
    KB_ARTICLES {
        bigserial id PK
        text title
        text body "the only source the AI may answer from"
        timestamptz updated_at
    }
    USERS {
        uuid id PK
        text email
    }
```

- **Messages vs notes.** A message is part of the email conversation with the student. A note is a private comment between staff. They're separate tables so a note can never be emailed by mistake.
- **Students aren't users.** A ticket stores the student's email and name directly; only staff are rows in `users`.
- **`version`** lets two agents edit the same ticket safely: the second save fails instead of silently overwriting the first.
- **`kb_articles` isn't linked to tickets.** The AI reads all articles for every ticket and cites the ones it used.

## 3c. The job queue

```mermaid
erDiagram
    SCHEDULED_TASKS {
        text task_name PK "process-ticket, send-email, key-rotation, ticket-auto-close"
        text task_instance PK "e.g. ticket-42-msg-97, email-uuid"
        bytea task_data "the job's data, serialized"
        timestamptz execution_time "when it is due"
        boolean picked "an instance is running it"
        text picked_by
        int consecutive_failures "drives retry backoff"
        timestamptz last_heartbeat
        bigint version
    }
```

This table belongs to the db-scheduler library, not to HelpDesk's own code. It's the reason background work survives a restart: a job is a row until it finishes. Section 4 shows rows being added and picked up.
