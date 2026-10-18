# Implementation Plan

See `project-scope.md` for what we're building and `tech-stack.md` for how.

## Phase 1: Project Setup

- [x] Create a monorepo with `backend/` and `frontend/` folders and initialize git
- [x] Set up a Spring Boot 4 project (Java 25, Maven) in `backend/`
- [x] Set up PostgreSQL with Docker Compose for local development
- [x] Configure Spring Data JPA and Flyway migrations
- [x] Set up JUnit 5 + Testcontainers with a base integration test
- [x] Add a consistent JSON error response for all endpoints (RFC 9457 problem+json)
- [x] Set up React + Vite + TypeScript in `frontend/` with a dev proxy to the backend

## Phase 2: Authentication

- [x] Create the `users` table and seed the first admin from `ADMIN_EMAIL` / `ADMIN_PASSWORD` (BCrypt)
- [x] Create the `signing_keys` table with private keys encrypted at rest
- [x] Generate an RSA signing key on first startup if none exists
- [x] Expose `GET /.well-known/jwks.json` with active and recently retired public keys
- [x] Issue RS256 access tokens (15 min) with `kid`, `sub`, `roles`, `jti`, `iat`, `exp`, `iss`, `aud`
- [x] Build `POST /auth/token` (email + password login)
- [x] Validate tokens with the Spring Security OAuth2 resource server (signature, `exp`, `iss`, `aud`)
- [x] Store refresh tokens hashed (SHA-256) in a `refresh_tokens` table
- [x] Build `POST /auth/refresh` with rotation on every use and reuse detection
- [x] Build `POST /auth/logout` to revoke the refresh token
- [x] Set the refresh token as an httpOnly, Secure, SameSite=Strict cookie for browser clients
- [x] Rotate signing keys on a schedule with db-scheduler (default every 7 days)
- [x] Rate-limit `POST /auth/token` per IP and per account (Bucket4j) and lock accounts after repeated failures
- [x] Record auth events in an `auth_events` audit table (never passwords or tokens)
- [x] Return 401 / 403 with a consistent JSON error body
- [x] Write the required auth tests (expired, tampered, wrong `aud`/`iss`, retired key, removed key, refresh reuse, logout, lockout)

## Phase 3: User Management

- [x] Add role-based access control for ADMIN and AGENT with `@PreAuthorize`
- [x] Build an admin-only endpoint to create agents
- [x] Build an admin-only endpoint to list users
- [x] Build an admin-only endpoint to edit users
- [x] Build an admin-only endpoint to deactivate users (and revoke their refresh tokens)

## Phase 4: Tickets

- [x] Create the tickets, messages, and notes tables (statuses: Open, Resolved, Closed; categories: General question, Technical question, Refund request)
- [x] Build the simulated inbound email endpoint that creates tickets or adds to an existing thread
- [x] Build the ticket access policy (admins see all; agents see unassigned tickets and their own; another agent's ticket → 403)
- [x] Build an endpoint for agents to claim an unassigned ticket (only one agent can win)
- [x] Build an endpoint to list tickets with filtering, sorting, search, and paging
- [x] Build an endpoint to fetch a single ticket with its thread and notes
- [x] Build endpoints to reply, add notes, change status, and correct the category
- [x] Build CRUD endpoints for knowledge base articles (agents read, admins write)
- [x] Build an admin-only dashboard stats endpoint (counts by status and category, AI vs. agent resolved)
- [x] Auto-close resolved tickets after 7 days with a db-scheduler job
- [x] Add Swagger UI (springdoc) with bearer auth and a Postman collection for auth and ticket endpoints

## Phase 5: AI Features

- [x] Integrate Claude through the Anthropic Java SDK (mocked in tests)
- [x] Process new tickets in the background with db-scheduler
- [x] Auto-classify tickets into a category (Haiku)
- [x] Generate an AI summary for each ticket (Haiku)
- [x] Generate a suggested reply from the knowledge base (Sonnet)
- [x] Auto-send personalized replies for FAQ emails the knowledge base answers (never for refunds)
- [x] Keep tickets open and retry when the AI fails or returns bad output

## Phase 6: Frontend

- [x] Keep the access token in memory and silently refresh once on a 401
- [x] Build the login page
- [x] Build the ticket list page with filters and sorting
- [x] Build the ticket detail page with the AI summary, claim button, and reply box

## Phase 7: Deployment

- [ ] Build one multi-arch Docker image where Spring Boot serves the React app
- [ ] Create a production Docker Compose file for the app and Postgres
- [ ] Add a GitHub Actions workflow to run tests on every push
- [ ] Add a GitHub Actions workflow to push the image to OCIR and deploy to the OCI VM over SSH
- [ ] Load secrets from OCI Vault or deploy-time environment variables
- [ ] Write `docs/oci-setup.md` for the manual OCI steps (compartment, IAM policies, VM, network rules)

## Phase 8: Observability

- [ ] Expose Micrometer metrics on a private Prometheus endpoint
- [ ] Add custom metrics for logins, token refreshes, reuse detection, tickets, and AI outcomes
- [ ] Add OpenTelemetry tracing across API, database, jobs, and Claude calls
- [ ] Run Prometheus and Grafana with Docker Compose and add a dashboard

## Phase 9 (Stretch): Email

- [ ] Send agent and AI replies over SMTP with Spring Mail
- [ ] Run Mailpit locally to catch outgoing emails
