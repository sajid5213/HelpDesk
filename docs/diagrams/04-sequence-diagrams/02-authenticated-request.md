# 4.2 An authenticated API call

[← 4.1 Login](01-login.md) · [Sequence list](README.md) · Next: [4.3 Token refresh →](03-token-refresh.md)

What happens to the access token on every request. Example: an agent opens ticket 42, `GET /api/tickets/42`.

```mermaid
sequenceDiagram
    autonumber
    actor U as Agent
    participant FE as Web app<br/>client.ts apiFetch
    participant F as Spring Security<br/>filter chain (API)
    participant D as JwtDecoder<br/>(JwtDecoderConfig)
    participant K as SigningKeyService
    participant TC as TicketController
    participant TS as TicketService
    participant P as TicketAccessPolicy
    participant DB as Postgres

    U->>FE: opens /tickets/42
    FE->>F: GET /api/tickets/42<br/>Authorization: Bearer eyJ...
    F->>F: BearerTokenResolver takes the token from the header
    F->>D: decode(token)
    D->>K: publishedKeys()
    K-->>D: ACTIVE key + recently RETIRED keys (public parts)
    D->>D: pick the key whose kid matches the token header
    D->>D: verify RS256 signature
    D->>D: check exp (30 s clock skew allowed), iss, aud
    alt bad signature, unknown kid, expired, wrong iss or aud, no token
        D-->>FE: 401 JSON problem (JsonAuthEntryPoint)
    end
    D-->>F: Jwt (claims)
    F->>F: roles claim ["AGENT"] becomes authority ROLE_AGENT
    F->>TC: request + Authentication
    TC->>TC: @PreAuthorize hasAnyRole ADMIN, AGENT
    alt role not allowed
        TC-->>FE: 403 JSON problem (JsonAccessDeniedHandler)
    end
    TC->>TS: detail(42, CurrentUser)
    TS->>DB: find ticket 42
    TS->>P: requireRead(user, ticket)
    alt ticket assigned to another agent
        P-->>FE: 403
    end
    TS->>DB: load messages, notes, staff names
    TS-->>TC: TicketDetail
    TC-->>FE: 200 JSON
    FE-->>U: renders the ticket page
```

## What to notice

- **No database lookup for the user.** The token alone proves who the caller is and which role they have. That's the point of a signed JWT: the server checks a signature instead of looking up a session.
- **Keys come straight from the database,** not over HTTP from our own JWKS URL (`JwtDecoderConfig`). The same `publishedKeys()` list feeds both, so a key removed from JWKS can no longer verify anything.
- **Three layers of permission:**
  1. *Authenticated?* A valid token, checked by the filter chain.
  2. *Right role?* `@PreAuthorize` on the controller (for example, only ADMIN may assign tickets).
  3. *This particular ticket?* `TicketAccessPolicy`: an agent may read unassigned tickets and their own, and change only their own.
- **Errors are JSON** (RFC 9457 problem details), never an HTML login page, because this is an API.
