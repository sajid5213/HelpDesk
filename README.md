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

## API tools

- **Swagger UI:** http://localhost:8090/swagger-ui.html. Log in with `POST /auth/token`, click **Authorize**, and paste the `accessToken`.
- **Postman:** import `postman/helpdesk.postman_collection.json` and `postman/helpdesk-local.postman_environment.json`, select the **HelpDesk local** environment, and set `adminPassword` (left empty in the file). Run **1 Auth / Login (admin)** first; it saves the tokens for every other request. A top-to-bottom run walks the whole flow: set up an agent, work a ticket, log in as the agent, then deactivate them.
- **From the command line:** `npx newman run postman/helpdesk.postman_collection.json -e postman/helpdesk-local.postman_environment.json --env-var adminPassword=<password>`

## Tests

```bash
cd backend
./mvnw verify      # starts its own PostgreSQL container; Docker must be running
```

```bash
cd frontend
npm run lint
npm run build
```

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
