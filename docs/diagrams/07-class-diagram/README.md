# 7. Class diagram

[← 6. Flowcharts](../06-flowcharts/README.md) · [All diagrams](../README.md)

Every class in `backend/src/main/java` as built (Phases 1–9), plus the web app's modules. GitHub draws the diagrams below.

**100** Java source files · **10** backend packages · **11** Flyway migrations · **182** backend tests · **10** frontend tests

## Packages

Code is split by feature, not by layer.

| Package | Job | Classes |
|---|---|---|
| `auth` | Login, refresh, logout, token checks, rate limits, lockout | AuthController, AuthService, AccessTokenIssuer, RefreshTokenService, RefreshToken, RefreshTokenRepository, LoginRateLimiter, LoginAttemptService, AuthCookieWriter, SecurityConfig, JwtDecoderConfig, JsonAuthEntryPoint, JsonAccessDeniedHandler |
| `keys` | RSA signing keys, encryption at rest, JWKS, rotation | SigningKey, KeyStatus, SigningKeyRepository, SigningKeyService, KeyEncryptionService, SigningKeyBootstrap, JwksController, KeyRotationTask |
| `user` | Accounts, the first admin, the admin user API, the signed-in user | User, Role, UserRepository, AdminBootstrap, PasswordConfig, PasswordPolicy, UserService, UserAdminController, MeController, CreateUserRequest, UpdateUserRequest, UserResponse, UserSessionsInvalidated |
| `audit` | Auth event log | AuthEvent, AuthEventType, AuthEventRepository, AuditService |
| `ticket` | Tickets, threads, the shared queue, stats, auto-close | Ticket, TicketMessage, TicketNote, TicketStatus, TicketCategory, AiStatus, MessageDirection, TicketRepository, TicketMessageRepository, TicketNoteRepository, TicketAccessPolicy, TicketService, TicketQuery, TicketController, TicketViews, TicketRequests, TicketLimits, StaffDirectory, InboundEmailService, InboundEmailController, InboundEmailRequest, TicketMessageReceived, StatsService, StatsController, AutoCloseService, AutoCloseTask |
| `kb` | Knowledge base articles | KbArticle, KbArticleRepository, KbArticleController |
| `ai` | Claude: classify, summarize, draft, auto-reply | AiClient, AnthropicAiClient, ClaudeCodeAiClient, DisabledAiClient, AiConfig, Classification, ReplyDraft, TicketThread, TicketAiPipeline, ProcessTicketTask, TicketAiScheduler, AiRefusedException, AiUnavailableException |
| `email` | Outgoing email through a transactional outbox | EmailSender, LoggingEmailSender, SmtpEmailSender, OutboundEmail, OutboundEmailRequested, EmailDispatcher, SendEmailTask |
| `common` | Errors, time, caller, metrics, tracing rules, the React routes | GlobalExceptionHandler, ApiException, NotFoundException, ProblemWriter, ClockConfig, CurrentUser, Emails, HelpdeskMetrics, ObservabilityConfig, SpaController |
| `config` | Typed settings, API docs | HelpdeskProperties, OpenApiConfig |
| `(root)` | Entry point | HelpdeskApplication |
| `frontend/src` | React app (TypeScript modules, not classes) | tokenStore, api/client, api/endpoints, api/types, AuthContext, router, AppShell, TicketBits, Toast, format, LoginPage, TicketListPage, TicketDetailPage, App |

## Accounts, keys, and audit tables

The JPA entities behind sign-in, one per table, and the repositories that read them. Relationships are foreign keys stored as UUIDs.

```mermaid
classDiagram
    direction LR

    class User {
        -UUID id
        -String email
        -String passwordHash
        -Role role
        -boolean active
        -int failedLoginCount
        -Instant lockedUntil
        -Instant createdAt
        +normalizeEmail(email)$ String
        +isLockedAt(now) boolean
    }
    class Role {
        <<enumeration>>
        ADMIN
        AGENT
    }
    class RefreshToken {
        -UUID id
        -UUID userId
        -String tokenHash
        -Instant expiresAt
        -Instant revokedAt
        -UUID replacedById
        -Instant createdAt
        ~rotateTo(next, now)
        ~revoke(now)
    }
    class SigningKey {
        -String kid
        -String publicJwk
        -String privateKeyEncrypted
        -KeyStatus status
        -Instant createdAt
        -Instant retiredAt
        ~retire(now)
    }
    class KeyStatus {
        <<enumeration>>
        ACTIVE
        RETIRED
    }
    class AuthEvent {
        -Long id
        -AuthEventType type
        -UUID userId
        -UUID actorId
        -String ip
        -Instant createdAt
    }
    class AuthEventType {
        <<enumeration>>
        LOGIN_SUCCESS
        LOGIN_FAILURE
        TOKEN_REFRESH
        TOKEN_REUSE_DETECTED
        LOGOUT
        KEY_ROTATED
        USER_CREATED
        USER_UPDATED
        USER_DEACTIVATED
        USER_REACTIVATED
        PASSWORD_CHANGED
        ROLE_CHANGED
    }
    class UserRepository {
        <<JpaRepository>>
        +findByEmailIgnoringCase(email) Optional~User~
    }
    class RefreshTokenRepository {
        <<JpaRepository>>
        +lockByHash(hash) Optional~RefreshToken~
        +revokeAllForUser(userId, now) int
    }
    class SigningKeyRepository {
        <<JpaRepository>>
        +lockActive() Optional~SigningKey~
    }
    class AuthEventRepository {
        <<JpaRepository>>
    }

    User --> Role
    User "1" <-- "*" RefreshToken : userId
    RefreshToken --> "0..1" RefreshToken : replacedById
    SigningKey --> KeyStatus
    AuthEvent --> AuthEventType
    AuthEvent --> "0..1" User : userId
    AuthEvent --> "0..1" User : actorId (admin)
    UserRepository ..> User
    RefreshTokenRepository ..> RefreshToken
    SigningKeyRepository ..> SigningKey
    AuthEventRepository ..> AuthEvent
```

- `users.email` is stored lowercased; the database rejects anything else.
- `refresh_tokens.token_hash` is the SHA-256 of the token; the raw value is never stored. `lockByHash` takes a row lock, so two refreshes with the same token run one after the other.
- A partial unique index allows only one `ACTIVE` row in `signing_keys`.

## Login, refresh, and logout

What runs behind `POST /auth/token`, `/auth/refresh` and `/auth/logout`. Arrows point from the caller to what it uses.

```mermaid
classDiagram
    direction TB

    class AuthController {
        <<RestController /auth>>
        ~token(LoginRequest) TokenResponse
        ~refresh(RefreshRequest, cookie) TokenResponse
        ~logout(RefreshRequest, cookie) 204
    }
    class AuthService {
        +login(email, password, ip) TokenPair
        +refresh(refreshToken, ip) TokenPair
        +logout(refreshToken, ip)
    }
    class AuthCookieWriter {
        ~isBrowser(request) boolean
        ~write(response, refreshToken)
        ~clear(response)
    }
    class LoginRateLimiter {
        +check(ip, email)
    }
    class LoginAttemptService {
        +isLocked(User) boolean
        +onFailure(User)
        +onSuccess(User)
    }
    class AccessTokenIssuer {
        +issue(User) IssuedAccessToken
    }
    class RefreshTokenService {
        +issue(User) String
        +rotate(raw, ip) Optional~Rotation~
        +revoke(raw)
        +revokeAllFor(userId)
    }
    class AuditService {
        +record(type, userId, ip)
    }
    class HelpdeskMetrics {
        +authEvent(type)
        +lockout()
        +rateLimited(scope)
    }
    class SigningKeyService {
        +activeKey() RSAKey
    }
    class PasswordEncoder {
        <<BCrypt bean>>
    }

    AuthController --> AuthService
    AuthController --> AuthCookieWriter
    AuthService --> LoginRateLimiter : 1. rate limit
    AuthService --> LoginAttemptService : 2. lockout
    AuthService --> PasswordEncoder : 3. password
    AuthService --> AccessTokenIssuer
    AuthService --> RefreshTokenService
    AuthService --> AuditService
    RefreshTokenService --> AuditService : reuse detected
    AccessTokenIssuer --> SigningKeyService : signs with active key
    AuditService --> HelpdeskMetrics : counts every event
    LoginRateLimiter --> HelpdeskMetrics
    LoginAttemptService --> HelpdeskMetrics
```

- `AuthService.login` checks in that order, so a flood of guesses is stopped before the slow BCrypt check. Unknown email and wrong password give the same 401 `invalid_credentials`.
- `RefreshTokenService.rotate` returns empty instead of throwing for a bad token, so when it detects reuse the "revoke the whole family" update still commits.
- With `X-Auth-Client: browser`, `AuthCookieWriter` puts the refresh token in an HttpOnly, SameSite=Strict cookie on `/auth`, and the response body leaves it out.
- Every audit event is also counted in `helpdesk_auth_events_total{type}`, so the dashboard shows logins, refreshes and token reuse from one place.

## Signing keys, token checks, and the security chains

How keys are created, published and rotated, and how every request is checked. Two filter chains: an open one for actuator on the private management port, and the API chain.

```mermaid
classDiagram
    direction LR

    class SigningKeyService {
        +ensureActiveKey() SigningKey
        +activeKey() RSAKey
        +rotate() SigningKey
        +publishedKeys() List~RSAKey~
    }
    class KeyEncryptionService {
        +encrypt(bytes) String
        +decrypt(text) bytes
    }
    class SigningKeyBootstrap {
        <<ApplicationRunner>>
    }
    class JwksController {
        <<GET /.well-known/jwks.json>>
    }
    class KeyRotationTask {
        <<db-scheduler, every 7 days>>
    }
    class JwtDecoderConfig {
        <<Configuration>>
        ~jwtDecoder() JwtDecoder
    }
    class SecurityConfig {
        <<Configuration>>
        ~managementFilterChain(http) Order 1
        ~securityFilterChain(http) Order 2
        ~jwtAuthenticationConverter()$
        ~bearerTokenResolver()$
    }
    class SpaController {
        <<GET /login, /tickets, /tickets/id>>
        ~app() forward index.html
    }
    class JsonAuthEntryPoint {
        <<401>>
    }
    class JsonAccessDeniedHandler {
        <<403>>
    }
    class ProblemWriter {
        +write(request, response, status, detail)
    }
    class AuditService

    SigningKeyService --> KeyEncryptionService : AES-256-GCM
    SigningKeyBootstrap --> SigningKeyService : first key
    JwksController --> SigningKeyService : public keys
    KeyRotationTask --> SigningKeyService : rotate
    KeyRotationTask --> AuditService : KEY_ROTATED
    JwtDecoderConfig --> SigningKeyService : published keys
    SecurityConfig --> JwtDecoderConfig
    SecurityConfig --> JsonAuthEntryPoint
    SecurityConfig --> JsonAccessDeniedHandler
    SecurityConfig ..> SpaController : React pages are public
    JsonAuthEntryPoint --> ProblemWriter
    JsonAccessDeniedHandler --> ProblemWriter
```

- `publishedKeys` returns the ACTIVE key plus RETIRED keys retired less than 20 minutes ago (15 min token lifetime + 5 min buffer). JWKS and token checking use this one list, so a key that leaves JWKS can no longer verify anything.
- The JWT decoder checks the signature, `exp`, `iss` and `aud`, with 30 seconds of clock skew. The `roles` claim becomes `ROLE_ADMIN` or `ROLE_AGENT`.
- `managementFilterChain` matches actuator endpoints only, which live on port 8091; Docker never publishes that port. On the public port, `/actuator/**` doesn't exist.
- The React app's pages and files are public (they hold no data); everything it shows comes from `/api`, which needs a token.

## User management and the signed-in user

The admin-only API at `/api/users`, the `/api/me` endpoint the web app uses, and how a change ends a user's sessions without `user` depending on `auth`.

```mermaid
classDiagram
    direction LR

    class UserAdminController {
        <<RestController /api/users, ADMIN only>>
        ~list(Pageable) Page~UserResponse~
        ~get(id) UserResponse
        ~create(CreateUserRequest) 201 UserResponse
        ~update(id, UpdateUserRequest) UserResponse
    }
    class MeController {
        <<GET /api/me>>
        ~me(jwt) UserResponse
    }
    class UserService {
        +list(Pageable) Page~UserResponse~
        +get(id) UserResponse
        +create(request, actorId, ip) UserResponse
        +update(id, request, actorId, ip) UserResponse
    }
    class CreateUserRequest {
        <<record>>
        email
        password
        role
    }
    class UpdateUserRequest {
        <<record, all optional>>
        email
        password
        role
        active
    }
    class UserResponse {
        <<record>>
        id
        email
        role
        active
        locked
        createdAt
    }
    class PasswordPolicy {
        +MIN_LENGTH = 12$
        +MAX_LENGTH = 128$
        +isAcceptable(password)$ boolean
    }
    class UserSessionsInvalidated {
        <<event record>>
        userId
    }
    class RefreshTokenService {
        +onUserSessionsInvalidated(event)
    }
    class AuditService
    class UserRepository
    class AdminBootstrap

    UserAdminController --> UserService
    MeController --> UserRepository
    MeController ..> UserResponse
    UserAdminController ..> CreateUserRequest
    UserAdminController ..> UpdateUserRequest
    UserService ..> UserResponse : returns
    UserService --> UserRepository
    UserService --> AuditService : actor = admin
    UserService ..> UserSessionsInvalidated : publishes
    RefreshTokenService ..> UserSessionsInvalidated : listens
    CreateUserRequest ..> PasswordPolicy
    UpdateUserRequest ..> PasswordPolicy
    AdminBootstrap ..> PasswordPolicy
```

- Deactivating, a password change or a role change publishes `UserSessionsInvalidated`; the `auth` package revokes that user's refresh tokens in the same transaction.
- No delete: users are deactivated, so the audit trail keeps pointing at real accounts. An admin can't deactivate or demote themselves (409 `cannot_modify_self`).
- The access token carries only the user id and roles, so the web app asks `/api/me` for the email and role.

## Tickets, the knowledge base, and the queue

The ticket entities and the services that work them. Every ticket endpoint goes through `TicketAccessPolicy`.

```mermaid
classDiagram
    direction LR

    class Ticket {
        -Long id
        -String subject
        -String studentEmail
        -String studentName
        -TicketStatus status
        -TicketCategory category
        -boolean categorySetManually
        -String aiSummary
        -String suggestedReply
        -boolean autoResolved
        -AiStatus aiStatus
        -Instant aiProcessedAt
        -UUID assigneeId
        -Instant createdAt
        -Instant updatedAt
        -Instant firstResponseAt
        -Instant resolvedAt
        -Instant closedAt
        -int version
        +isFrom(email) boolean
        +isUnassigned() boolean
        ~replied(now)
        ~resolve(now)
        ~reopen(now)
        ~close(now)
        ~assignTo(userId, now)
    }
    class TicketMessage {
        -Long id
        -Long ticketId
        -MessageDirection direction
        -UUID authorUserId
        -String body
        -boolean sentByAi
        -Instant createdAt
    }
    class TicketNote {
        -Long id
        -Long ticketId
        -UUID authorUserId
        -String body
        -Instant createdAt
    }
    class TicketStatus {
        <<enumeration>>
        OPEN
        RESOLVED
        CLOSED
    }
    class TicketCategory {
        <<enumeration>>
        GENERAL_QUESTION
        TECHNICAL_QUESTION
        REFUND_REQUEST
    }
    class AiStatus {
        <<enumeration>>
        PENDING
        DONE
        FAILED
        SKIPPED
    }
    class KbArticle {
        -Long id
        -String title
        -String body
        -Instant createdAt
        -Instant updatedAt
    }
    class TicketController {
        <<RestController /api/tickets>>
        ~list(filters, Pageable) Page~TicketSummary~
        ~get(id) TicketDetail
        ~claim(id) TicketSummary
        ~reply(id, body) TicketDetail
        ~note(id, body) TicketDetail
        ~update(id, status, category) TicketDetail
        ~assign(id, assigneeId) TicketSummary
    }
    class TicketService {
        +detail(id, user) TicketDetail
        +claim(id, user) TicketSummary
        +reply(id, body, user) TicketDetail
        +addNote(id, body, user) TicketDetail
        +update(id, request, user) TicketDetail
        +assign(id, assigneeId) TicketSummary
    }
    class TicketAccessPolicy {
        +canRead(user, ticket) boolean
        +canUpdate(user, ticket) boolean
        +requireRead(user, ticket)
        +requireUpdate(user, ticket)
    }
    class TicketQuery {
        <<record>>
        status, category, assignee, q, from, to
        ~toSpecification(user) Specification
    }
    class TicketRepository {
        <<JpaRepository + Specification>>
        +claimIfUnassigned(id, agentId, now) int
        +closeResolvedBefore(before, now) int
    }
    class StaffDirectory {
        ~emails(ids) Map~UUID, String~
    }
    class TicketViews {
        <<records>>
        TicketSummary
        TicketDetail
        MessageView
        NoteView
    }
    class InboundEmailService {
        +receive(InboundEmailRequest) Result
    }
    class TicketMessageReceived {
        <<event>>
    }
    class StatsService {
        +current() Stats
    }
    class AutoCloseTask {
        <<db-scheduler, hourly>>
    }
    class AutoCloseService {
        +closeStaleResolvedTickets() int
    }
    class KbArticleController {
        <<RestController /api/kb/articles>>
    }
    class CurrentUser {
        <<record>>
        UUID id
        boolean admin
    }
    class OutboundEmailRequested {
        <<event>>
    }

    Ticket --> TicketStatus
    Ticket --> "0..1" TicketCategory
    Ticket --> AiStatus
    Ticket "1" *-- "*" TicketMessage : thread
    Ticket "1" *-- "*" TicketNote : notes
    TicketController --> TicketService
    TicketController ..> TicketQuery : list
    TicketController ..> CurrentUser
    TicketService --> TicketAccessPolicy
    TicketService --> TicketRepository
    TicketService --> StaffDirectory : names for owners, authors
    TicketService ..> TicketViews : returns
    TicketService ..> OutboundEmailRequested : reply
    InboundEmailService --> TicketRepository
    InboundEmailService ..> TicketMessageReceived : publishes
    AutoCloseTask --> AutoCloseService
    AutoCloseService --> TicketRepository
    KbArticleController ..> KbArticle
```

- `TicketAccessPolicy`: admins read and change every ticket; agents read unassigned tickets and their own, and change only their own. Another agent's ticket is a 403.
- `claimIfUnassigned` is one conditional `UPDATE ... WHERE assignee_id IS NULL`: when two agents claim at once, one gets 0 rows and a 409 `already_assigned`. `@Version` turns any other lost update into a 409 `conflict`.
- `StaffDirectory` looks up agent emails for a whole page in one query, so the web app shows names instead of UUIDs.
- `InboundEmailService` continues a thread only when the `[#id]` reference matches and the sender is the ticket's student. A resolved ticket reopens; a closed one starts a new ticket.

## AI pipeline

How a stored student email becomes a category, a summary, and either an automatic reply or a draft for an agent.

```mermaid
classDiagram
    direction LR

    class AiClient {
        <<interface>>
        +isEnabled() boolean
        +classify(TicketThread) Classification
        +draftReply(TicketThread, articles) ReplyDraft
    }
    class AnthropicAiClient {
        <<mode api>>
        -AnthropicClient client
        -String classifyModel
        -String replyModel
    }
    class ClaudeCodeAiClient {
        <<mode claude-code, local only>>
        -List~String~ command
        -Duration timeout
    }
    class DisabledAiClient {
        <<mode off>>
    }
    class AiConfig {
        <<Configuration>>
        ~aiClient(properties, json) AiClient
    }
    class Classification {
        <<record, structured output>>
        TicketCategory category
        String summary
        boolean mentionsRefund
    }
    class ReplyDraft {
        <<record, structured output>>
        boolean answeredByKb
        List~Long~ articleIds
        String reply
    }
    class TicketThread {
        <<record>>
        Long ticketId
        String subject
        String studentName
        List~Message~ messages
    }
    class TicketAiScheduler {
        <<after commit>>
        ~onMessageReceived(TicketMessageReceived)
    }
    class ProcessTicketTask {
        <<db-scheduler one-time, retried>>
        ~run(pipeline, Job)$
    }
    class TicketAiPipeline {
        +process(ticketId, newTicket) Outcome
        +markSkipped(ticketId)
        +markFailed(ticketId)
        ~mayAutoReply(...)$ boolean
        -call(operation, supplier) T
    }
    class AiRefusedException
    class AiUnavailableException
    class HelpdeskMetrics {
        +aiOutcome(outcome)
    }
    class OutboundEmailRequested {
        <<event>>
    }

    AiClient <|.. AnthropicAiClient
    AiClient <|.. ClaudeCodeAiClient
    AiClient <|.. DisabledAiClient
    AiConfig ..> AiClient : picks by helpdesk.ai.mode
    AnthropicAiClient ..> Classification
    AnthropicAiClient ..> ReplyDraft
    AnthropicAiClient ..> AiRefusedException : refusal
    AnthropicAiClient ..> AiUnavailableException : errors, bad output
    TicketAiScheduler --> ProcessTicketTask : schedules
    ProcessTicketTask --> TicketAiPipeline
    TicketAiPipeline --> AiClient
    TicketAiPipeline ..> TicketThread
    TicketAiPipeline --> HelpdeskMetrics
    TicketAiPipeline ..> OutboundEmailRequested : auto-reply
```

- `mayAutoReply` answers by itself only when every check passes: general or technical question, no refund wording, answered by the knowledge base with real article ids, still unassigned, first message. Otherwise the draft becomes `suggestedReply`.
- Claude is called outside any database transaction; the results are applied in a new transaction on a freshly loaded ticket, so an agent's claim during the call is seen.
- `AiRefusedException` isn't retried (ticket SKIPPED, goes to an agent). `AiUnavailableException` is retried with backoff, then FAILED.
- Each run is one trace, `helpdesk.ai.process`, with `claude classify` and `claude draft_reply` spans (`helpdesk.ai.call` timer, tagged operation, mode, outcome) and its SQL nested inside.

## Email outbox

Replies to students, from agents and from the AI, become background jobs stored with the reply, then go out over SMTP.

```mermaid
classDiagram
    direction LR

    class OutboundEmailRequested {
        <<event record>>
        OutboundEmail email
    }
    class OutboundEmail {
        <<record, Serializable>>
        String to
        String subject
        String body
        Long ticketId
    }
    class EmailDispatcher {
        ~onOutboundEmail(event) schedule job in caller's transaction
        ~sendNow(event) after commit
    }
    class SendEmailTask {
        <<db-scheduler one-time send-email, retried>>
    }
    class EmailSender {
        <<interface>>
        +send(OutboundEmail)
    }
    class SmtpEmailSender {
        <<helpdesk.email.mode=smtp>>
        -JavaMailSender mail
        -String from
    }
    class LoggingEmailSender {
        <<helpdesk.email.mode=log, default>>
    }
    class HelpdeskMetrics {
        +emailSent(sent)
    }
    class TicketService
    class TicketAiPipeline

    TicketService ..> OutboundEmailRequested : agent reply
    TicketAiPipeline ..> OutboundEmailRequested : AI auto-reply
    OutboundEmailRequested *-- OutboundEmail
    EmailDispatcher ..> OutboundEmailRequested : listens
    EmailDispatcher --> SendEmailTask : stores job
    SendEmailTask --> EmailSender
    SendEmailTask --> HelpdeskMetrics : sent or failed
    EmailSender <|.. SmtpEmailSender
    EmailSender <|.. LoggingEmailSender
```

- The job row is written through db-scheduler's transaction-aware DataSource, so it commits or rolls back with the reply: no email for a rolled-back reply, and no lost email for a saved one.
- A failed send (mail server down) is retried 5 times with growing delays, then counted as `helpdesk_emails_total{outcome="failed"}`. The reply stays on the ticket either way.
- Locally the SMTP server is Mailpit, which catches every email. `OutboundEmail.toString()` leaves the body out, so it never reaches the logs.

## Metrics and tracing

The app's own counters, the backlog gauge, and the rule that keeps background spans out of Tempo. Prometheus scrapes port 8091; traces go to Tempo over OTLP.

```mermaid
classDiagram
    direction LR

    class HelpdeskMetrics {
        +authEvent(type)
        +lockout()
        +rateLimited(scope)
        +ticketOpened()
        +aiOutcome(outcome)
        +emailSent(sent)
    }
    class ObservabilityConfig {
        <<Configuration>>
        ~dueJobsGauge(jdbc) MeterBinder
        ~noOrphanBackgroundSpans() ObservationPredicate
    }
    class MeterRegistry {
        <<Micrometer, Prometheus>>
    }
    class ObservationRegistry {
        <<Micrometer, OpenTelemetry bridge>>
    }
    class AuditService
    class LoginRateLimiter
    class LoginAttemptService
    class InboundEmailService
    class TicketAiPipeline
    class SendEmailTask

    HelpdeskMetrics --> MeterRegistry
    ObservabilityConfig ..> MeterRegistry : helpdesk.jobs.due
    ObservabilityConfig ..> ObservationRegistry : filters jdbc and security spans
    AuditService --> HelpdeskMetrics
    LoginRateLimiter --> HelpdeskMetrics
    LoginAttemptService --> HelpdeskMetrics
    InboundEmailService --> HelpdeskMetrics
    TicketAiPipeline --> HelpdeskMetrics
    TicketAiPipeline --> ObservationRegistry : ai.process, ai.call
    SendEmailTask --> HelpdeskMetrics
```

- Tags are a few fixed values (event type, outcome, scope), never a user id, email or ticket id, so metrics hold no personal data.
- SQL spans come from datasource-micrometer with query text only, never parameter values. `noOrphanBackgroundSpans` keeps SQL and security-filter spans only inside a real trace, so the scheduler's polling and Prometheus scrapes don't flood Tempo.
- Free from the libraries: HTTP, JVM, Hikari pool and `dbscheduler_task_*` metrics.

## Startup, settings, and errors

Shared pieces the other packages depend on.

```mermaid
classDiagram
    direction LR

    class HelpdeskProperties {
        <<record, prefix helpdesk>>
        +auth() Auth
        +admin() Admin
        +tickets() Tickets
        +ai() Ai
        +email() Email
    }
    class Auth {
        <<record>>
        issuer, audience
        accessTokenTtl, refreshTokenTtl
        keyRotationInterval, keyRetireBuffer
        keyEncryptionKey
        lockout, rateLimit
    }
    class Admin {
        <<record>>
        email
        password
    }
    class Tickets {
        <<record>>
        autoCloseAfter
    }
    class Ai {
        <<record>>
        mode api, claude-code, off
        apiKey, classifyModel, replyModel
        maxAttempts, retryDelay
        +enabled() boolean
    }
    class Email {
        <<record>>
        mode log, smtp
        from
        maxAttempts, retryDelay
    }
    class AdminBootstrap {
        <<ApplicationRunner>>
    }
    class ClockConfig {
        ~clock() Clock
    }
    class GlobalExceptionHandler {
        <<RestControllerAdvice>>
    }
    class ApiException {
        -HttpStatus status
        -String errorCode
        -Map headers
    }
    class NotFoundException

    HelpdeskProperties *-- Auth
    HelpdeskProperties *-- Admin
    HelpdeskProperties *-- Tickets
    HelpdeskProperties *-- Ai
    HelpdeskProperties *-- Email
    AdminBootstrap --> HelpdeskProperties
    GlobalExceptionHandler ..> ApiException
    GlobalExceptionHandler ..> NotFoundException
```

- The app refuses to start if `HELPDESK_KEK` is missing or isn't 32 bytes. Secrets are redacted from `toString()`.
- Everything time-based reads the `Clock` bean; tests swap in a clock they can move forward.
- Errors are RFC 9457 problem+json with a stable `error` code (`invalid_credentials`, `account_locked`, `rate_limited`, `already_assigned`, `ticket_closed`, `conflict`, ...) plus headers such as `Retry-After`.

## Web app modules

The React app is TypeScript modules and components rather than classes; this shows what depends on what.

```mermaid
classDiagram
    direction LR

    class tokenStore {
        <<module>>
        getToken() string
        setToken(token)
    }
    class client {
        <<module api/client>>
        apiFetch(path, init) T
        refreshSession() single-flight
        login(email, password)
        logout()
        ApiError
    }
    class endpoints {
        <<module api/endpoints>>
        fetchMe()
        listTickets(query)
        getTicket(id)
        claimTicket(id)
        sendReply(id, body)
        addNote(id, body)
        updateTicket(id, change)
    }
    class AuthContext {
        <<React context>>
        checking, signedOut, signedIn
        signIn()
        signOut()
    }
    class router {
        <<module>>
        RouterProvider
        useRouter()
        matchPath()
        Link
    }
    class App {
        <<component>>
    }
    class AppShell {
        <<component>>
        views with live counts
    }
    class LoginPage {
        <<page>>
    }
    class TicketListPage {
        <<page>>
        filters in the URL
    }
    class TicketDetailPage {
        <<page>>
        thread, AI summary, reply, notes
    }

    client --> tokenStore : memory only
    endpoints --> client
    AuthContext --> client
    AuthContext --> endpoints : fetchMe
    App --> AuthContext
    App --> router
    App --> AppShell
    App --> LoginPage
    App --> TicketListPage
    App --> TicketDetailPage
    TicketListPage --> endpoints
    TicketDetailPage --> endpoints
    AppShell --> endpoints : counts
```

- The access token lives only in `tokenStore`, a module variable, never in localStorage or sessionStorage. The refresh token is the HttpOnly cookie.
- On a 401, `apiFetch` runs one shared refresh (parallel requests reuse the same promise, so the backend's reuse detection never fires), then retries once; if that fails, the app returns to sign in.
- `router` is a small History API router (`/login`, `/tickets`, `/tickets/:id`); the queue's filters live in the URL, so every view is linkable.
