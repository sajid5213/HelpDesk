# 2. Deployment: where each part runs

[← 1. System context](../01-system-context/README.md) · [All diagrams](../README.md) · Next: [3. Data model →](../03-data-model/README.md)

The same code runs in two setups. Learn the dev one first: it's what you'll run.

## 2a. Development (your machine)

The backend and the web app run directly on your machine; Postgres and Mailpit run in Docker (`docker-compose.yml`).

```mermaid
flowchart LR
    you(["👤 You, in a browser"])

    subgraph host["Your machine"]
        vite["<b>Vite dev server</b><br/>:5173<br/>React app with hot reload"]
        boot["<b>Spring Boot</b> (./mvnw spring-boot:run)<br/>API :8090 · management :8091"]
        cli["claude CLI<br/>(AI mode claude-code)"]
        subgraph docker["Docker: docker-compose.yml"]
            pg[("Postgres 18<br/>:5432")]
            mailpit["Mailpit<br/>SMTP :1025 · inbox :8025"]
        end
    end

    you -- "http://localhost:5173" --> vite
    vite -- "proxies /api, /auth,<br/>/.well-known" --> boot
    boot --> pg
    boot -- "SMTP<br/>(HELPDESK_EMAIL_MODE=smtp)" --> mailpit
    boot -- "runs claude -p" --> cli
    you -. "read sent emails<br/>http://localhost:8025" .-> mailpit
```

- **Why the Vite proxy?** The browser sees one origin (`localhost:5173`) for both the pages and the API. That's needed for the `SameSite=Strict` refresh-token cookie.
- **AI locally** runs through the Claude Code CLI on your Pro plan, so there is no API bill.
- **Email** defaults to `log` mode (printed, not sent). Set `HELPDESK_EMAIL_MODE=smtp` to send to Mailpit.

## 2b. Production-style stack (`compose.prod.yaml`)

Everything runs in Docker on one private network. Only the app's HTTP port is published.

```mermaid
flowchart LR
    users(["👤 Agents / Admins"])
    ops(["👤 You, on the server"])

    subgraph net["Docker network: helpdesk-prod"]
        app["<b>app</b> (helpdesk image)<br/>React + Spring Boot, non-root user<br/>:8080 public · :8091 private"]
        pg[("<b>postgres</b><br/>no published port")]
        mailpit["<b>mailpit</b><br/>SMTP :1025"]
        subgraph obs["Optional: compose.observability.yaml"]
            prom["Prometheus"]
            tempo["Tempo"]
            graf["Grafana"]
        end
    end
    claude["Claude API<br/>(AI mode api, needs a key)"]

    users -- "http://host:8080<br/>(HELPDESK_HTTP_PORT)" --> app
    app --> pg
    app -- "SMTP" --> mailpit
    app -- "HTTPS" --> claude
    prom -- "scrapes app:8091" --> app
    app -. "OTLP traces :4318" .-> tempo
    graf --> prom
    graf --> tempo
    ops -. "127.0.0.1:8026 inbox<br/>127.0.0.1:3000 Grafana" .-> mailpit
    ops -.-> graf
```

| Port | Published? | Why |
|---|---|---|
| app 8080 | Yes | The only public entry point: web app and API. |
| app 8091 | **No** | Health and metrics. Only Prometheus, inside the network. |
| postgres 5432 | **No** | Only the app talks to the database. |
| mailpit 8026, Grafana 3000, Prometheus 9090 | Only on `127.0.0.1` | Visible from the server itself, never from the internet. |

Secrets (`DB_PASSWORD`, `HELPDESK_KEK`, `ADMIN_PASSWORD`, `ANTHROPIC_API_KEY`) come from an `.env.prod` file kept outside git. None are in the image.

## 2c. How the image is built (`Dockerfile`)

```mermaid
flowchart LR
    src["Source code"] --> web["<b>Stage 1: web</b><br/>node:24-alpine<br/>npm ci, npm run build"]
    src --> build["<b>Stage 2: build</b><br/>temurin:25-jdk<br/>./mvnw package"]
    web -- "frontend/dist copied into<br/>Spring's static files" --> build
    build -- "only the JAR" --> run["<b>Stage 3: run</b><br/>temurin:25-jre<br/>non-root user, port 8080"]
```

The final image has a JRE and one JAR: no Node, no Maven, no source code. That keeps it small and leaves less to attack.
