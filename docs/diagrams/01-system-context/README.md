# 1. System context and containers

[← All diagrams](../README.md) · Next: [2. Deployment →](../02-deployment/README.md)

## 1a. Context: who uses HelpDesk, and what it talks to

The whole system is one box here. Around it: the people who use it and the outside systems it depends on.

```mermaid
flowchart LR
    student(["👤 Student<br/>(the customer)"])
    agent(["👤 Agent<br/>(support staff)"])
    admin(["👤 Admin"])

    helpdesk["<b>HelpDesk</b><br/>AI-assisted support tickets"]

    claude["Claude<br/>(Anthropic)"]
    mail["Mail server<br/>(SMTP; Mailpit locally)"]
    obs["Prometheus · Tempo · Grafana<br/>(optional monitoring)"]

    student -- "sends an email<br/>(today: an admin posts it<br/>to /api/inbound/emails)" --> helpdesk
    helpdesk -- "reply emails" --> mail
    mail -- "delivers" --> student
    agent -- "works the ticket queue<br/>in the web app" --> helpdesk
    admin -- "manages users,<br/>sees every ticket" --> helpdesk
    helpdesk -- "classify, summarize,<br/>draft a reply" --> claude
    obs -. "scrapes metrics,<br/>receives traces" .- helpdesk
```

What to notice:
- **Students never log in.** They only send and receive email. Only agents and admins have accounts.
- **Incoming email is simulated.** There's no mail-provider webhook yet; an admin posts the email to `POST /api/inbound/emails`, and `InboundEmailService` treats it exactly as a webhook would.
- **Claude is optional.** With AI off, every ticket simply goes to the agents.

## 1b. Containers: the running parts inside HelpDesk

Now open the HelpDesk box. A "container" here means a separately running program or data store (not necessarily a Docker container).

```mermaid
flowchart TB
    browser(["👤 Agent / Admin<br/>in a browser"])

    subgraph hd["HelpDesk"]
        direction TB
        spa["<b>Web app</b><br/>React + TypeScript<br/>login, ticket queue, ticket page"]
        subgraph boot["<b>Backend</b>: one Spring Boot process (Java 25)"]
            direction LR
            api["<b>REST API</b><br/>/auth/** · /api/** ·<br/>/.well-known/jwks.json"]
            jobs["<b>Background jobs</b><br/>db-scheduler:<br/>process-ticket · send-email ·<br/>key-rotation · ticket-auto-close"]
            mgmt["<b>Management port</b><br/>health · Prometheus metrics"]
        end
        db[("<b>PostgreSQL</b><br/>users, tokens, keys, tickets,<br/>KB articles, job queue")]
    end

    claude["Claude<br/>API or Claude Code CLI"]
    smtp["Mail server<br/>(Mailpit)"]
    prom["Prometheus"]
    tempo["Tempo"]

    browser -- "HTTPS" --> spa
    spa -- "JSON over HTTP<br/>Bearer access token" --> api
    api -- "JDBC (JPA)" --> db
    jobs -- "JDBC: picks due jobs<br/>from scheduled_tasks" --> db
    jobs -- "HTTPS / CLI" --> claude
    jobs -- "SMTP" --> smtp
    prom -- "scrapes :8091" --> mgmt
    boot -. "OTLP traces" .-> tempo
```

What to notice:
- **One backend process does everything:** it answers HTTP requests *and* runs the background jobs. They share the database.
- **The database is also the job queue.** Background work (AI processing, sending email) is a row in the `scheduled_tasks` table, so it survives restarts and runs once even with several app instances.
- **The web app holds no data of its own.** Everything it shows comes from `/api`. In production the React files are built into the Spring Boot image and served by the same process.
- **The management port is separate** and never published to the internet; only Prometheus on the private network reaches it.
