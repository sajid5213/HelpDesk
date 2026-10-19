# 4.8 An agent claims and replies; the email goes out

[← 4.7 AI processing](07-ai-processing.md) · [Sequence list](README.md) · Next: [4.9 Auto-close →](09-auto-close.md)

## Claim: take a ticket from the shared queue

```mermaid
sequenceDiagram
    autonumber
    actor AG as Agent
    participant TC as TicketController
    participant TS as TicketService
    participant DB as Postgres

    AG->>TC: POST /api/tickets/42/claim
    TC->>TS: claim(42, me)
    alt already mine
        TS-->>AG: 200 (nothing changes)
    end
    TS->>DB: UPDATE tickets SET assignee_id = me<br/>WHERE id = 42 AND assignee_id IS NULL
    alt 0 rows updated: another agent got there first
        TS-->>AG: 409 already_assigned
    else 1 row updated
        TS-->>AG: 200, ticket is mine
    end
```

If two agents click Claim at the same moment, the database decides: the `WHERE assignee_id IS NULL` condition can be true for only one of them.

## Reply: the transactional outbox

```mermaid
sequenceDiagram
    autonumber
    actor AG as Agent
    participant TC as TicketController
    participant TS as TicketService
    participant ED as EmailDispatcher
    participant DB as Postgres
    participant SCH as db-scheduler
    participant SE as SendEmailTask
    participant MS as EmailSender<br/>(SMTP or log)
    participant MP as Mail server

    AG->>TC: POST /api/tickets/42/replies {body}
    TC->>TS: reply(42, body, me)
    Note over TS,DB: one database transaction starts
    TS->>TS: TicketAccessPolicy.requireUpdate (403 if not mine)
    alt ticket CLOSED
        TS-->>AG: 409 ticket_closed
    end
    TS->>DB: insert ticket_messages (OUTBOUND, author me)
    TS->>DB: ticket RESOLVED, first_response_at if first reply
    TS->>ED: publish OutboundEmailRequested(to, "Re: [#42] subject", body)
    ED->>DB: insert scheduled_tasks row send-email (IN THE SAME transaction)
    Note over TS,DB: commit: the reply and the email job are saved together, or neither is
    TC-->>AG: 201, the updated ticket
    ED->>SCH: after commit: triggerCheckForDueExecutions() (don't wait for the next poll)

    SCH->>SE: execute(OutboundEmail)
    SE->>MS: send(email)
    MS->>MP: SMTP: From HelpDesk Support, To the student
    alt mail server reachable
        MP-->>MS: 250 OK
        SE-->>SCH: done: job row deleted, metric sent
    else mail server down
        MP--xMS: connection refused / timeout
        SE--xSCH: failure
        SCH->>SCH: retry with backoff: 30 s, then longer
        Note over SCH: after 5 attempts: give up, log a warning,<br/>metric failed. The reply itself stays saved.
    end
```

## What to notice

- **The outbox pattern.** The email isn't sent inside the request. Instead, a "send this email" job is written to the database *in the same transaction* as the reply. If the reply rolls back, so does the job (no email for a reply that doesn't exist). If the reply commits, the job is guaranteed to exist (no reply without its email).
- **Why that's needed:** sending first and saving second can email the student about a reply that then fails to save. Saving first and sending second can lose the email if the app crashes between the two steps. The outbox avoids both.
- **"Re: [#42] subject"** puts the ticket number in the subject, so the student's answer threads back to ticket 42 (see 4.6).
- **Two senders:** `SmtpEmailSender` really sends (to Mailpit locally); `LoggingEmailSender` only logs. `helpdesk.email.mode` picks one.
- **The AI's automatic reply (4.7) uses exactly this path** from step 8 onward.
