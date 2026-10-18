# HelpDesk

AI-assisted support ticket system for a coding-course business. Spring Boot backend with hand-built JWT auth, minimal React frontend, Claude for classification and replies.

- What we're building: [`project-scope.md`](project-scope.md)
- How: [`tech-stack.md`](tech-stack.md)
- Progress: [`implementation-plan.md`](implementation-plan.md)

## Prerequisites

- JDK 25 (`JAVA_HOME` must point to it; Maven is provided by the wrapper)
- Docker (for PostgreSQL and Testcontainers)
- Node.js 20+

## Run locally

```bash
# 0. Local secrets (once): copy the example and fill in real values
cp backend/.env.example backend/.env
#    HELPDESK_KEK:   openssl rand -base64 32
#    ADMIN_EMAIL / ADMIN_PASSWORD: the first admin, created on first start (password >= 12 chars)
#    ANTHROPIC_API_KEY: optional; turns on AI classification and replies

# 1. Database (local-only throwaway credentials: helpdesk / helpdesk)
docker compose up -d

# 2. Backend on http://localhost:8090 (set PORT to change it)
cd backend
./mvnw spring-boot:run

# 3. Frontend on http://localhost:5173 (proxies /api, /auth, /.well-known to the backend)
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 and sign in with `ADMIN_EMAIL` / `ADMIN_PASSWORD`. The web app has three screens: sign in, the ticket queue (open / assigned to me / unassigned, with status, category and search filters), and a ticket page with the thread, AI summary, claim, AI-drafted reply, status changes and internal notes.

How the web app signs in: the access token lives only in memory (never in localStorage or sessionStorage). The refresh token is an HttpOnly, SameSite=Strict cookie scoped to `/auth`, so page scripts can't read it. On a 401 the app refreshes once, shared across parallel requests, and retries; if that fails it returns to sign in. Reloading the page restores the session from the cookie.

## Run with Docker (production-style)

One image holds everything: Spring Boot serves the API and the built React app. `compose.prod.yaml` runs it with its own Postgres. That database is separate from the dev one and isn't reachable from outside.

```bash
cp .env.prod.example .env.prod     # gitignored; fill in DB_PASSWORD, HELPDESK_KEK, ADMIN_EMAIL, ADMIN_PASSWORD
docker compose -f compose.prod.yaml --env-file .env.prod up -d --build
# open http://localhost:8080 (or the HELPDESK_HTTP_PORT you set)
docker compose -f compose.prod.yaml --env-file .env.prod down   # add -v to delete its database
```

- **No secrets in the image:** they come from `.env.prod` at run time.
- **`prod` profile:** turns off Swagger UI and the API docs.
- **AI is off by default:** the Claude Code CLI can't run inside the container. Set `HELPDESK_AI_MODE=api` and `ANTHROPIC_API_KEY` to use the Claude API, which is billed per use.
- **Plain HTTP works only on localhost.** The refresh cookie is `Secure`, so any other host needs HTTPS.

## Observability (metrics, traces, dashboard)

```bash
docker compose -f compose.observability.yaml up -d      # Prometheus, Tempo, Grafana
# then start the backend with traces on: add TRACING_EXPORT_ENABLED=true to backend/.env
# with the Docker stack instead:
docker compose -f compose.prod.yaml -f compose.observability.yaml --env-file .env.prod up -d
```

- **Grafana:** http://localhost:3000 (user `admin`, password `GRAFANA_ADMIN_PASSWORD`, default `admin` locally). Open **Dashboards → HelpDesk → HelpDesk** for the dashboard. It has four rows:
  - **Auth:** logins, refreshes, token reuse, lockouts, rate limits, admin actions.
  - **Tickets and AI:** tickets opened, AI outcomes, Claude latency and errors.
  - **Background jobs:** due jobs, runs and duration.
  - **API:** requests, errors and latency.
- **Traces:** Grafana → **Explore → Tempo**. A student email shows as `http post /api/inbound/emails`. The AI work is its own trace, `ai process ticket`: the database reads, `claude classify`, `claude draft_reply` and the writes all nest under it. The AI job runs later on the scheduler, so its trace isn't linked to the request that queued it.
- **Raw metrics:** `curl localhost:8091/actuator/prometheus`. Actuator runs on its own management port, which is never published by Docker; Prometheus scrapes it over the private network. The public port (8090/8080) doesn't serve it.
- **What's never recorded:** passwords, tokens, email addresses or email bodies. Metric tags are fixed values (event type, outcome), and SQL spans carry the query text only, never the values.

## API tools

- **Swagger UI:** http://localhost:8090/swagger-ui.html. Log in with `POST /auth/token`, click **Authorize**, and paste the `accessToken`.
- **Postman:** import `postman/helpdesk.postman_collection.json` and `postman/helpdesk-local.postman_environment.json`, select the **HelpDesk local** environment, and set `adminPassword` (left empty in the file). Run **1 Auth / Login (admin)** first; it saves the tokens for every other request. A top-to-bottom run walks the whole flow: set up an agent, work a ticket, log in as the agent, then deactivate them.
- **From the command line:** `npx newman run postman/helpdesk.postman_collection.json -e postman/helpdesk-local.postman_environment.json --env-var adminPassword=<password>`

## Tests

```bash
cd backend
./mvnw verify      # starts its own PostgreSQL container; Docker must be running; never calls Claude
ANTHROPIC_API_KEY=sk-... ./mvnw test -Dgroups=live -Dtest.excludedGroups=   # optional: real Claude calls (costs a little)
```

```bash
cd frontend
npm run test       # Vitest: token handling and sign-in messages
npm run lint
npm run build
```

On GitHub, `.github/workflows/ci.yml` runs all of the above on every push and pull request, then checks that the Docker image builds.

## Try the auth API

```bash
# Log in (API clients get both tokens in the body)
curl -s -H 'Content-Type: application/json'   -d '{"email":"admin@helpdesk.local","password":"<ADMIN_PASSWORD>"}'   localhost:8090/auth/token

# Public signing keys
curl -s localhost:8090/.well-known/jwks.json

# Rotate the refresh token (the old one stops working; reusing it logs you out everywhere)
curl -s -H 'Content-Type: application/json' -d '{"refreshToken":"<refreshToken>"}' localhost:8090/auth/refresh

# Log out
curl -s -H 'Content-Type: application/json' -d '{"refreshToken":"<refreshToken>"}' localhost:8090/auth/logout
```

Browsers send `X-Auth-Client: browser` and get the refresh token as an `HttpOnly; Secure; SameSite=Strict` cookie instead of in the body.

## Manage users (admin only)

```bash
TOKEN=<admin accessToken>

# Create an agent (role defaults to AGENT; passwords need 12+ characters)
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json'   -d '{"email":"agent@helpdesk.local","password":"agent-password-123"}' localhost:8090/api/users

# List (newest first, max 100 per page) and get one
curl -s -H "Authorization: Bearer $TOKEN" 'localhost:8090/api/users?page=0&size=20'
curl -s -H "Authorization: Bearer $TOKEN" localhost:8090/api/users/<id>

# Change any of email, password, role, active
curl -s -X PATCH -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json'   -d '{"active":false}' localhost:8090/api/users/<id>
```

- Users are deactivated, never deleted, so the audit trail stays complete.
- Deactivating a user or changing their password or role revokes their refresh tokens. An access token already issued keeps working until it expires (at most 15 minutes): the usual trade-off of stateless JWTs.
- Admins cannot deactivate themselves or change their own role, so there is always at least one active admin.

## Work tickets

```bash
ADMIN=<admin accessToken>; AGENT=<agent accessToken>

# Simulate a student's email (admin only; stands in for a mail provider webhook)
curl -s -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json'   -d '{"fromEmail":"sam@example.com","fromName":"Sam","subject":"Cannot open module 3","body":"The video will not load."}'   localhost:8090/api/inbound/emails

# Agents see unassigned tickets and their own; filter, search, sort, page
curl -s -H "Authorization: Bearer $AGENT" 'localhost:8090/api/tickets?assignee=unassigned&status=OPEN&q=module&sort=createdAt,desc'

# Claim, reply (resolves the ticket and emails the student), add an internal note
curl -s -X POST -H "Authorization: Bearer $AGENT" localhost:8090/api/tickets/1/claim
curl -s -H "Authorization: Bearer $AGENT" -H 'Content-Type: application/json' -d '{"body":"Clear your cache and reload."}' localhost:8090/api/tickets/1/replies
curl -s -H "Authorization: Bearer $AGENT" -H 'Content-Type: application/json' -d '{"body":"Old course version."}' localhost:8090/api/tickets/1/notes
```

- A student email that mentions `[#1]` in the subject (or sends `ticketRef`) from the same address goes onto ticket 1, and reopens it if it was resolved. A closed ticket, an unknown number, or a different sender starts a new ticket.
- Status moves: OPEN and RESOLVED switch back and forth; either can be CLOSED; CLOSED is final. Resolved tickets close automatically after 7 days without a reply.
- Until real email exists, replies are logged (recipient and ticket only), not sent.

## AI (Claude)

Turn AI on in one of two ways (otherwise tickets get `aiStatus: SKIPPED` and agents handle them):

- **Claude API:** `ANTHROPIC_API_KEY=sk-ant-...` in `backend/.env`. Billed per use; the production path.
- **Claude Code, free on your Claude plan (local testing only):** `HELPDESK_AI_MODE=claude-code` in `backend/.env`. The backend runs `claude -p` (Claude Code's command line, signed in with your own subscription) with no tools, no saved sessions, and none of your settings or plugins, and never passes an API key to it. It counts against your plan's usage limits, takes a few seconds per call, and only works where Claude Code is installed and logged in, so not in Docker or on OCI.

Then every student email is handled by a background job:

1. **Classify and summarize** with Claude Haiku 5.5: category, a one-line summary for agents, and whether the student asks for money back in any wording.
2. **Draft a reply** with Claude Sonnet 5.5, using only the knowledge base articles.
3. **Answer automatically only if all of these hold:** it is a general or technical question, the student does not mention a refund, the articles fully answer it (and the cited articles exist), nobody has claimed the ticket yet, and it is the ticket's first message. The ticket is then resolved and the reply emailed.
4. **Otherwise** the draft is saved as `suggestedReply` for an agent. Refunds always land here.

The ticket's `aiStatus` shows where it is: `PENDING`, `DONE`, `FAILED` (Claude kept failing after 5 tries with backoff), or `SKIPPED` (no API key, or Claude declined the request). `FAILED` and `SKIPPED` tickets simply stay with the agents.

Student emails are treated as untrusted: they are wrapped in tags with `<` and `>` escaped, and the prompts tell Claude never to follow instructions inside them or to promise refunds.

Try it with **8 AI demo** in the Postman collection.
