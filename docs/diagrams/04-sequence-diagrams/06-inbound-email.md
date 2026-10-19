# 4.6 A student's email becomes a ticket

[← 4.5 Signing keys](05-signing-keys.md) · [Sequence list](README.md) · Next: [4.7 AI processing →](07-ai-processing.md)

A student emails support. Today an admin simulates the mail provider by posting the email to `POST /api/inbound/emails`; a real provider webhook would call the same service.

```mermaid
sequenceDiagram
    autonumber
    actor ST as Student
    participant MP as Mail provider<br/>(simulated by an admin)
    participant IC as InboundEmailController
    participant IS as InboundEmailService
    participant DB as Postgres
    participant EV as Spring events
    participant AS as TicketAiScheduler
    participant SCH as db-scheduler

    ST->>MP: email: "Can't log in to the course"
    MP->>IC: POST /api/inbound/emails {fromEmail, fromName, subject, body}<br/>(ADMIN role required)
    IC->>IS: receive(email)
    Note over IS,DB: one database transaction starts
    IS->>IS: look for a ticket number: ticketRef field,<br/>or a [#id] tag in the subject
    alt number found AND same student AND ticket not CLOSED
        IS->>DB: load that ticket
        IS->>IS: receivedMessage(): a RESOLVED ticket goes back to OPEN
    else no number, someone else's ticket, or CLOSED
        IS->>DB: insert a new ticket: OPEN, ai_status PENDING
    end
    IS->>DB: insert ticket_messages row (INBOUND, the body)
    IS->>EV: publish TicketMessageReceived(ticketId, messageId, newTicket)
    Note over IS,DB: transaction commits
    IC-->>MP: 201 Created, Location: /api/tickets/{id}

    EV->>AS: onMessageReceived (runs only AFTER the commit)
    alt AI turned off
        AS->>DB: ai_status = SKIPPED (agents handle it)
    else AI on
        AS->>SCH: scheduleIfNotExists(process-ticket,<br/>id ticket-{id}-msg-{messageId}, due now)
        SCH->>DB: insert scheduled_tasks row
    end
    Note over SCH: The AI work happens later, in the background: see 4.7
```

## What to notice

- **Threading needs two matches.** A reply joins an existing ticket only if the ticket number matches *and* the sender is that ticket's student. A student can't write into someone else's ticket just by guessing a number.
- **A CLOSED ticket is never reopened** by email; a new ticket starts instead.
- **The request returns fast.** Calling Claude takes seconds, so it isn't done inside the HTTP request. The request only stores the email and queues a job.
- **Why wait for the commit before queuing the AI job?** If the transaction rolled back, a job queued earlier would point at a ticket that doesn't exist. `@TransactionalEventListener(AFTER_COMMIT)` guarantees the ticket is saved first.
- **One job per message.** The job ID includes the message ID, and `scheduleIfNotExists` ignores duplicates, so the same event can't run the AI twice.
