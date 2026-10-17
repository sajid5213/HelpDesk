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
