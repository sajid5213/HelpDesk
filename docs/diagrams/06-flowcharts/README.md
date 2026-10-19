# 6. Flowcharts: how the tricky decisions are made

[← 5. State machines](../05-state-machines/README.md) · [All diagrams](../README.md) · Next: [7. Class diagram →](../07-class-diagram/README.md)

A sequence diagram shows *who* talks to *whom*. A flowchart zooms in on one decision and shows every branch. Most important first.

## 6.1 Who may do what with a request

Every HTTP request goes through these checks, in this order.

```mermaid
flowchart TD
    req([HTTP request]) --> port{Which port?}
    port -- "8091 management" --> mgmt["Management chain: open<br/>(health, metrics)<br/>port is never published"]
    port -- "8090 / 8080 public" --> pub{Public path?<br/>/auth/token, refresh, logout<br/>/.well-known/jwks.json<br/>the React app's pages and files<br/>Swagger in dev}
    pub -- yes --> ok1([Allowed])
    pub -- no --> tok{Valid Bearer token?<br/>signature, kid, exp, iss, aud}
    tok -- no --> e401([401 JSON problem])
    tok -- yes --> role{Role allowed by<br/>@PreAuthorize?}
    role -- no --> e403a([403])
    role -- yes --> isTicket{Is it about<br/>one ticket?}
    isTicket -- no --> ok2([Allowed])
    isTicket -- yes --> admin{ADMIN?}
    admin -- yes --> ok3([Allowed: every ticket])
    admin -- "no, AGENT" --> rw{Reading or<br/>changing?}
    rw -- reading --> r{Unassigned<br/>or mine?}
    rw -- changing --> w{Mine?}
    r -- yes --> ok4([Allowed])
    r -- no --> e403b([403: assigned to<br/>another agent])
    w -- yes --> ok5([Allowed])
    w -- no --> e403c([403: claim it first])
```

| Check | Where in the code |
|---|---|
| Which chain | `SecurityConfig`: `managementFilterChain` (order 1), `securityFilterChain` (order 2) |
| Token | `JwtDecoderConfig` |
| Role | `@PreAuthorize` on `TicketController`, `UserAdminController`, `InboundEmailController`, ... |
| This ticket | `TicketAccessPolicy` |

## 6.2 May the AI answer the student by itself?

`TicketAiPipeline.mayAutoReply`. **Every** condition must be true; one "no" means the draft is only suggested to an agent.

```mermaid
flowchart TD
    start([AI has a category, summary and draft]) --> c1{Brand-new ticket<br/>with exactly one message?}
    c1 -- no --> sug
    c1 -- yes --> c2{Still OPEN<br/>and unassigned?}
    c2 -- no --> sug
    c2 -- yes --> c3{Category GENERAL_QUESTION<br/>or TECHNICAL_QUESTION?}
    c3 -- "no (e.g. REFUND_REQUEST)" --> sug
    c3 -- yes --> c4{Classifier says<br/>no refund mentioned?}
    c4 -- no --> sug
    c4 -- yes --> c5{Draft says the KB<br/>fully answers it?}
    c5 -- no --> sug
    c5 -- yes --> c6{Cites at least one KB article,<br/>and every cited ID really exists?}
    c6 -- no --> sug
    c6 -- yes --> c7{Reply text<br/>not empty?}
    c7 -- no --> sug
    c7 -- yes --> auto([Send automatically:<br/>save AI message, RESOLVED,<br/>auto_resolved, queue email])
    sug([Suggest only:<br/>save as suggested_reply<br/>for an agent to edit and send])
```

The design rule behind it: the AI may act alone only where a mistake is cheap. Money (refunds), follow-up conversations, and anything a person already owns always go to a human.

## 6.3 Does an incoming email start a new ticket?

`InboundEmailService.receive`.

```mermaid
flowchart TD
    mail([Incoming email]) --> ref{ticketRef given, or<br/>a ticket tag like Re: ... 42<br/>in the subject?}
    ref -- no --> new
    ref -- yes --> exists{Ticket with that<br/>number exists?}
    exists -- no --> new
    exists -- yes --> same{Sender is that<br/>ticket's student?}
    same -- no --> new
    same -- yes --> closed{Ticket CLOSED?}
    closed -- yes --> new
    closed -- no --> thread([Add to that ticket's thread<br/>RESOLVED becomes OPEN again])
    new([Create a new OPEN ticket])
    thread --> job([Queue the AI job<br/>after commit])
    new --> job
```

## 6.4 The login checks

`AuthService.login`. The order is the point: the cheapest checks run first, and the slow password hash runs last.

```mermaid
flowchart TD
    start([POST /auth/token]) --> rl{Under the rate limit?<br/>20 per IP, 5 per email,<br/>per minute}
    rl -- no --> e429([429 rate_limited<br/>+ Retry-After])
    rl -- yes --> user{Email belongs<br/>to a user?}
    user -- no --> dummy[Check against a dummy hash<br/>to take the same time] --> fail([401 invalid_credentials])
    user -- yes --> lock{Account locked?}
    lock -- yes --> e401l([401 account_locked])
    lock -- no --> pw{Password correct<br/>and account active?}
    pw -- no --> count[failed_login_count + 1<br/>lock at 5] --> fail
    pw -- yes --> reset[Reset the count] --> issue([200: access token<br/>+ refresh cookie])
```

Every outcome, success or failure, is written to `auth_events`.
