# HelpDesk — Project Scope

> Status: **Draft** (2026-10-16). Items marked *(assumption)* are not yet confirmed. See [Open Questions](#9-open-questions).

## 1. Background

We run online coding courses. Students contact support by email with questions about course access, content, billing, certificates, and refunds (for example, when they are not satisfied with a course).

We receive **hundreds of support emails per day**. Today, agents manually read, classify, and respond to each one. This is:

- **Slow**: students wait too long for answers.
- **Impersonal**: agents fall back on canned responses to keep up.

## 2. Solution

A ticket management system that uses AI to **automatically classify, respond to, and route** support tickets, delivering faster, more personalized responses to students while freeing agents to focus on complex issues.

## 3. Goals

- Turn every incoming support email into a ticket automatically.
- Answer frequently asked questions automatically, using a knowledge base.
- Make every AI response **personalized**: addressed to the student, aware of their course and their specific question, written in a human, friendly tone (not canned).
- Reduce the time agents spend reading and classifying tickets.
- Route complex issues (e.g. refunds) to human agents with an AI summary and a suggested reply ready.

### Non-goals (for the first version)
- A student-facing portal or login. Students interact by email only *(assumption)*.
- Live chat, phone, or social media channels.
- Processing refunds or payments inside the helpdesk. Agents approve refunds here, but the money is handled in the existing payment system *(assumption)*.

### Anti-goals (would count as failure even if it "works")
- The AI sends wrong or made-up information (e.g. promises a refund or a policy that doesn't exist).
- Responses still feel templated or robotic.
- Students get stuck in an AI loop with no way to reach a human.

## 4. Users and Roles

| Role | Who | Can do |
|------|-----|--------|
| **Admin** | Support lead / owner | Everything an agent can do, plus user management and knowledge base management *(assumption)* |
| **Agent** | Support staff | View, filter, and work on tickets; review, edit, and send AI-suggested replies |
| **Student** | Course customer | Sends emails and receives replies; no login |

## 5. Features

### 5.1 Email intake → tickets
- Receive support emails and create a ticket for each new email.
- Replies from the student on the same thread are added to the existing ticket, not a new one *(assumption)*.
- Store sender, subject, body, timestamps, and attachments.

### 5.2 AI ticket classification
- Automatically assign each ticket to **exactly one** category:
  - **General question**
  - **Technical question**
  - **Refund request**
- Agents can correct the category manually.
- No separate priority field in the first version *(our call)*.

### 5.3 Knowledge base
- A collection of FAQ articles and policies (e.g. refund policy, how to access courses, certificate rules).
- The AI uses it as the source of truth when writing responses.
- Admins can add and edit articles through the API (Swagger); no screen in the first version *(our call, same as 5.9 and 5.10)*.

### 5.4 AI auto-responses (personalized)
- **Only FAQ emails are auto-answered.** An "FAQ email" is a General or Technical question that a knowledge base article clearly answers *(our call)*. For these, the AI generates a human-friendly, personalized reply from the knowledge base and sends it automatically, with no human check.
- **Refund requests always go to an agent.** The AI never sends a reply on a refund ticket by itself.
- All other tickets go to an agent as well.
- Personalization uses **only what's in the email**: the student's name, the course they mention, and a direct answer to what they actually asked. No lookup of enrollment or purchase records.
- Replies are saved on the ticket and emailed to the student over SMTP (`tech-stack.md` §8); locally Mailpit catches them. The ticket moves to Resolved when the reply is saved, and the email is retried in the background if the mail server is down *(our call)*.
- Safety net *(our call)*: if an email looks like an FAQ but the knowledge base doesn't clearly answer it, the AI does not guess. It routes the ticket to an agent instead.

### 5.5 AI summaries
- A short summary at the top of each ticket so agents understand the issue without reading the whole thread.

### 5.6 AI suggested replies
- For tickets routed to agents, the AI drafts a reply based on the knowledge base and the conversation.
- Agents can send it as-is, edit it, or write their own.

### 5.7 Ticket list
- List all tickets with **filtering** (status, category, assigned agent, date) and **sorting** (newest, oldest, recently updated).
- Search by student email or subject *(assumption)*.
- **No automatic assignment.** All agents work from one shared list and pick tickets themselves.
- Picking a ticket assigns it to that agent; from then on, other agents can't open or change it (see `tech-stack.md` §3).
- Agents see **their own** and **unassigned** tickets, with a "Mine / Unassigned" filter. Admins see all tickets *(our call)*.

### 5.8 Ticket detail view
- Full email thread, AI summary, category, status, assigned agent.
- AI suggested reply with edit/send controls.
- Status changes between **Open**, **Resolved**, and **Closed** (the only three statuses), plus internal notes *(assumption)*.

### 5.9 Dashboard (API only for now)
- Exposed as an API endpoint and used through Swagger. There is no dashboard screen in the first version.
- Returns an overview of all tickets: counts by status and category, how many were auto-resolved by AI vs. handled by agents, and response times *(assumption)*.

### 5.10 User management (Admin only, API only for now)
- Exposed as API endpoints and used through Swagger. There are no user management screens in the first version.
- The system is **deployed with one admin account** already created (set up during installation, e.g. from configuration). There is no public sign-up.
- The admin creates additional **agent** accounts, and can edit and deactivate them.
- Login for admins and agents.

## 6. Ticket Lifecycle

Tickets have exactly three statuses: **Open**, **Resolved**, **Closed**. The flow below between them is *(assumption)*.

1. Email arrives → ticket created (status: **Open**).
2. AI classifies and summarizes the ticket.
3. **FAQ email, clearly answered by the knowledge base:** AI sends a personalized reply → status **Resolved**.
4. **Refund requests and everything else:** ticket is routed to an agent with an AI summary and suggested reply → agent reviews and sends → **Resolved**.
5. If the student replies, the ticket reopens.
6. Resolved tickets close after a period with no reply.

## 7. Success Measures

- Faster first-response time for students.
- A meaningful share of tickets resolved automatically by AI.
- Less agent time spent per ticket.
- Students perceive replies as personal, not canned.

## 8. Technical Approach

The full tech stack, auth design, testing, deployment, observability, and email plans are in **[`tech-stack.md`](tech-stack.md)**.

### Build order

1. Auth: schema → key management + JWKS → token issuance → validation → refresh/logout → authorization rules → rate limit + audit → tests.
2. Ticket features on top of the new auth (including the db-scheduler AI jobs).
3. Minimal frontend.
4. Docker + OCI deploy + CI.
5. Observability: metrics, tracing, Prometheus + Grafana *(our call to place it here; basic Actuator metrics can be switched on earlier)*.
6. Stretch: email (Spring Mail + Mailpit).

Each step is planned and approved before implementation; tests must pass before moving on.

## 9. Open Questions

1. ~~**Auto-send boundary**~~ — **Decided:** only FAQ emails are auto-sent; refunds always go to agents.
2. **Refund policy:** what are the rules (e.g. within X days, % of course completed)? The AI uses these when drafting a suggested reply for the agent.
3. ~~**Student data**~~ — **Decided:** no student records; personalization uses only the student's name and what they write in the email.
4. ~~**Email setup**~~ — **Decided:** nothing exists yet; simulate email during development and plug in a real provider later (see `tech-stack.md` §8).
5. ~~**Tech stack**~~ — **Decided:** see `tech-stack.md` (Java 25 / Spring Boot 4, hand-built JWT auth, db-scheduler, minimal React, Claude Haiku/Sonnet, PostgreSQL, Micrometer/OpenTelemetry/Prometheus/Grafana, OCI free tier).
6. ~~**Agent assignment**~~ — **Decided:** shared list; agents pick tickets themselves (see 5.7).
7. ~~**Scale**~~ — *(our call)*: designed for a small team (up to ~20 agents) and hundreds of emails per day; no special scaling work in the first version.
