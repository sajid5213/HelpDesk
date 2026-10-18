# HelpDesk — Tech Stack

> Product scope: see `project-scope.md`.
> **Learning goal:** backend auth built the way an Identity and Access Management (IAM) service does it. Backend and auth depth come before frontend polish.
> **Rule:** no dependency outside this file gets added without asking first.

## 1. Summary

- **Frontend:** React, TypeScript, Vite, plain CSS / Tailwind
- **Backend:** Java 25, Spring Boot 4, Maven
- **Database:** PostgreSQL, Spring Data JPA, Flyway
- **AI:** Claude (Haiku/Sonnet) via Anthropic Java SDK
- **Auth:** Spring Security + Nimbus JOSE (JWT access tokens, refresh tokens, JWKS, key rotation)
- **Job Queue:** db-scheduler
- **Rate limiting:** Bucket4j
- **API docs:** springdoc-openapi + Swagger UI
- **Observability:** Micrometer, OpenTelemetry, Prometheus, Grafana
- **Email:** Spring Mail, Mailpit (stretch)
- **Testing:** JUnit 5, Testcontainers
- **Deploy:** Docker, OCI Container Registry, OCI Compute, GitHub Actions

## 2. Backend

| Area | Choice |
|------|--------|
| Language / framework | Java 25 (LTS), Spring Boot 4 |
| Build | Maven (wrapper included). Runs locally on port 8090 (`PORT` env var overrides; containers use 8080) |
| Database | PostgreSQL, via Spring Data JPA (Hibernate) |
| Migrations | Flyway |
| Job queue / background work | **db-scheduler**: tasks stored in PostgreSQL, so jobs survive restarts and never run twice (AI processing, signing-key rotation) |
| AI | Anthropic Java SDK (`com.anthropic`). **Claude Haiku 5.5** (`claude-haiku-5-5`) for classification and summaries; **Claude Sonnet 5.5** (`claude-sonnet-5-5`) for writing replies *(our call on which model does which)* |
| Rate limiting | Bucket4j (in-memory) |
| API docs / manual testing | springdoc-openapi + Swagger UI (bearer auth); Postman collection + environment in `postman/` (runnable with Newman) |

**AI usage:**
- `helpdesk.ai.mode`: `api` (Anthropic Java SDK, the default and production path), `claude-code` (local testing through the Claude Code CLI on a Claude subscription, no API billing), or `off`.
- Classification and other machine-read results use structured JSON output with a fixed schema.
- Replies are grounded in knowledge base articles; when they don't cover the question, the ticket goes to an agent.
- The API key stays in server configuration, never in the frontend.

## 3. Auth (token-based, hand-built — no session login, no Spring Authorization Server)

**Token endpoints**
- `POST /auth/token`: email + password → access token (JWT, 15 min) + refresh token (opaque random string, 7 days).
- `POST /auth/refresh`: returns a new access token and a new refresh token. Rotated on every use; the old one becomes invalid.
- `POST /auth/logout`: revokes the given refresh token.
- Refresh tokens are stored only as SHA-256 hashes in `refresh_tokens` (user_id, token_hash, expires_at, revoked_at, created_at).
- Reuse detection: presenting a revoked or rotated refresh token revokes all of that user's refresh tokens.
- Passwords: BCrypt via Spring Security `PasswordEncoder`.

**Signing and keys**
- Access tokens signed RS256 with Nimbus JOSE + JWT (bundled with Spring Security). Every token has a `kid` header.
- `signing_keys` table: kid, public key, private key (encrypted at rest; key-encryption key from env), status (ACTIVE / RETIRED), created_at, retired_at.
- `GET /.well-known/jwks.json` (public): ACTIVE keys plus RETIRED keys that are still within access-token TTL + buffer.
- Rotation as a recurring db-scheduler task, default every 7 days (configurable): new ACTIVE key, previous one RETIRED. A key is generated on first startup if none exists.

**Validation**
- Spring Security OAuth2 Resource Server (JWT), using the app's own keys directly (no HTTP call to its own JWKS).
- Checks signature, `exp`, `iss`, `aud`. Claims: `sub` (user id), `roles`, `jti`, `iat`, `exp`, `iss`, `aud`. `roles` maps to Spring authorities.

**Authorization**
- Roles `ADMIN` and `AGENT`, enforced with `@PreAuthorize`.
- Resource rules in one dedicated authorization component:
  - **ADMIN:** can read and update all tickets.
  - **AGENT:** can see and read **unassigned** tickets and **pick** (claim) one, which assigns it to them. Can read and update tickets **assigned to them**. Any ticket assigned to **another agent** → 403.
  - Claiming is safe when two agents pick the same ticket at once: only one succeeds, and the other gets a clear "already assigned" error *(our call)*.
- 401 for authentication failures, 403 for authorization failures, one consistent JSON error body: RFC 9457 `application/problem+json` (Spring `ProblemDetail`), used for every error in the API.

**Hardening**
- Rate limit on `POST /auth/token` per IP and per account (in-memory, Bucket4j). Temporary account lock after N consecutive failures (configurable).
- `auth_events` audit table: LOGIN_SUCCESS, LOGIN_FAILURE, TOKEN_REFRESH, TOKEN_REUSE_DETECTED, LOGOUT, KEY_ROTATED, with user_id (nullable), ip, timestamp. Passwords and tokens are never logged.

**First admin:** created at startup from `ADMIN_EMAIL` / `ADMIN_PASSWORD` if it doesn't exist.

## 4. Frontend (minimal)

- React + Vite + TypeScript. Three screens only: **Login**, **Ticket list**, **Ticket detail with reply box**.
- Access token kept in memory only (never localStorage). Refresh token set by the backend as an `httpOnly`, `Secure`, `SameSite=Strict` cookie.
- On a 401, call `/auth/refresh` once and retry.
- Plain CSS or minimal Tailwind. **Out of scope:** dashboard charts, component libraries (shadcn/MUI), form libraries, UI polish.

## 5. Testing

- JUnit 5 + Testcontainers (real PostgreSQL). Claude calls are mocked.
- Required auth tests: valid/invalid login (with audit event), expired token, tampered signature, wrong `aud`/`iss`, RETIRED-but-published key accepted, removed key rejected, refresh rotation and reuse detection, refresh after logout rejected, AGENT vs ADMIN ticket access (another agent's ticket → 403 for AGENT, 200 for ADMIN), AGENT can read and claim an unassigned ticket, rate limit / lockout.

## 6. Deployment (OCI free tier)

- One Docker image: Spring Boot serves the built React app. Multi-arch build (`linux/arm64` at minimum).
- OCI Compute: Ampere A1 (ARM64) VM running Docker; app, Postgres, Prometheus, and Grafana as containers via Docker Compose.
- Registry: OCI Container Registry (OCIR).
- CI/CD: GitHub Actions → test → build image → push to OCIR → SSH to VM → `docker compose pull && docker compose up -d`.
- Secrets (`ADMIN_PASSWORD`, key-encryption key, `ANTHROPIC_API_KEY`, DB password) from OCI Vault, or env injected at deploy time if Vault doesn't fit the free tier. Never in the repo or the image.
- `docs/oci-setup.md` documents the manual OCI steps (compartment, deploy user/group, least-privilege IAM policies for OCIR and Vault, VM and security list rules). The developer applies all OCI changes.

## 7. Observability

- **Metrics:** Micrometer (via Spring Boot Actuator), exposed at a Prometheus endpoint. Besides the built-in JVM/HTTP/database metrics, custom metrics for logins (success/failure), token refreshes, reuse detections, lockouts, tickets created, AI auto-resolved vs. sent to agents, AI call latency and errors, and job queue backlog *(our call on which custom metrics)*.
- **Tracing:** OpenTelemetry (Spring Boot's `spring-boot-starter-opentelemetry`, OTLP export), so one request can be followed from the API through the database, the job queue, and the Claude call. SQL spans come from `datasource-micrometer` (query text only, never parameter values). Traces are stored in **Grafana Tempo**, so metrics and traces share one Grafana.
- **Prometheus** collects the metrics; **Grafana** shows them in dashboards; **Tempo** stores the traces. Both run as containers in Docker Compose, locally and on the OCI VM.
- Metrics and traces never contain passwords, tokens, or email bodies. The Actuator and Prometheus endpoints live on a separate management port (8091) that is never published; only Prometheus reaches it over the private Docker network *(our call)*.

## 8. Email (stretch goal)

- For now, inbound email is simulated through an API endpoint only. Replies are stored on the ticket.
- Real email is built only after auth, frontend, deployment, and observability are done.
- **Built (Phase 9):** Spring Mail sends replies over SMTP, behind the same email sender interface as the stand-in, so nothing else changed. **Mailpit** runs in Docker Compose to catch and show those emails. Each email is a db-scheduler job stored in the same transaction as the reply (transactional outbox), retried with backoff, so a mail-server outage never loses an email.
- **Going live later (not planned):** Mailpit can't receive real email from students. If the app ever handles real email, **Mailgun** is the chosen provider: Mailgun Routes forward incoming emails to a webhook (HMAC signature checked, old or reused requests rejected), and outgoing mail uses Mailgun SMTP. This needs a domain with Mailgun's DNS and MX records.
