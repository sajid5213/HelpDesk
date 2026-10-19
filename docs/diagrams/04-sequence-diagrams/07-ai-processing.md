# 4.7 The AI processes the ticket

[← 4.6 Inbound email](06-inbound-email.md) · [Sequence list](README.md) · Next: [4.8 Agent reply and email →](08-agent-reply-and-email.md)

A background job, a moment after 4.6. No HTTP request is involved: db-scheduler finds the due job in the database and runs it.

```mermaid
sequenceDiagram
    autonumber
    participant SCH as db-scheduler
    participant PT as ProcessTicketTask
    participant P as TicketAiPipeline
    participant DB as Postgres
    participant AI as AiClient<br/>(API, Claude Code CLI, or off)
    participant ED as EmailDispatcher

    SCH->>DB: poll: any due jobs?
    DB-->>SCH: process-ticket, ticket-42-msg-97
    SCH->>PT: execute(Job(ticketId 42, messageId 97, newTicket true))
    PT->>P: process(42, newTicket)

    P->>DB: read-only transaction: the ticket, its whole thread, all KB articles
    alt ticket gone or CLOSED
        P-->>PT: SKIPPED (nothing to do)
    end

    Note over P,AI: Claude is called OUTSIDE any database transaction<br/>(a slow call must not hold a connection or locks)
    P->>AI: classify(thread)
    AI-->>P: Classification(category, summary, mentionsRefund)
    P->>AI: draftReply(thread, knowledge base)
    AI-->>P: ReplyDraft(reply, answeredByKb, articleIds)

    Note over P,DB: new write transaction
    P->>DB: applyAi(): category (unless an agent set it), summary, ai_status DONE
    P->>P: mayAutoReply()? (see the flowchart in folder 6)
    alt safe to answer alone
        P->>DB: insert ticket_messages (OUTBOUND, sent_by_ai)
        P->>DB: ticket RESOLVED, auto_resolved = true
        P->>ED: OutboundEmailRequested (send-email job, same transaction: see 4.8)
    else anything else
        P->>DB: suggested_reply = the draft (an agent will review it)
    end
    Note over P,DB: commit
    P-->>PT: AUTO_REPLIED or SUGGESTED
    PT-->>SCH: done: job row deleted

    alt Claude unavailable (network, timeout, 5xx)
        AI--xP: AiUnavailableException
        P--xSCH: job fails
        SCH->>SCH: retry later with exponential backoff (30 s, then longer)
        Note over SCH: after 5 failed attempts:<br/>markFailed(): ai_status FAILED, agents take over
    else Claude declines the request
        AI--xP: AiRefusedException
        PT->>DB: markSkipped(): ai_status SKIPPED, no retry
    end
```

## What to notice

- **Three AI modes, one interface.** `AiClient` has three implementations: `AnthropicAiClient` (Claude API), `ClaudeCodeAiClient` (runs the `claude` CLI locally, no API bill) and `DisabledAiClient`. `AiConfig` picks one from `helpdesk.ai.mode`. The pipeline doesn't know which one it's talking to.
- **Two models.** A small, fast model classifies (`claude-haiku-5-5`); a stronger one writes the reply (`claude-sonnet-5-5`).
- **The AI only answers from the knowledge base.** The draft must cite KB article IDs that really exist, or it isn't sent.
- **Auto-reply is the exception, not the rule.** Only a brand-new ticket with one message, unassigned, a general or technical question, no mention of refunds, and a KB-backed answer. Everything else becomes a suggestion an agent can edit.
- **Failures never lose the ticket.** It's already saved (4.6). The worst case is `FAILED` or `SKIPPED`, and an agent handles it as normal.
- **Retry vs give up.** A temporary problem (Claude down) is retried; a refusal isn't, because asking again would get the same answer.
