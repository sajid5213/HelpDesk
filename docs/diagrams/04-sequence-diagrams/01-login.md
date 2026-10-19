# 4.1 Login

[← Sequence list](README.md) · Next: [4.2 An authenticated API call →](02-authenticated-request.md)

An agent types their email and password into the web app. Endpoint: `POST /auth/token`.

```mermaid
sequenceDiagram
    autonumber
    actor U as Agent
    participant FE as Web app<br/>LoginPage + client.ts
    participant C as AuthController
    participant S as AuthService
    participant RL as LoginRateLimiter
    participant LA as LoginAttemptService
    participant DB as Postgres
    participant RT as RefreshTokenService
    participant AT as AccessTokenIssuer
    participant K as SigningKeyService
    participant A as AuditService

    U->>FE: email + password, click Sign in
    FE->>C: POST /auth/token {email, password}<br/>header X-Auth-Client: browser
    C->>S: login(email, password, ip)

    S->>RL: check(ip, email)
    alt more than N tries this minute from this IP or for this email
        RL-->>FE: 429 rate_limited + Retry-After header
    end

    S->>DB: findByEmailIgnoringCase(email)
    alt no such user
        S->>S: compare password with a dummy hash<br/>(takes the same time as a real check)
        S->>A: LOGIN_FAILURE
        S-->>FE: 401 invalid_credentials
    else account locked (locked_until in the future)
        S->>A: LOGIN_FAILURE
        S-->>FE: 401 account_locked
    else wrong password, or account deactivated
        S->>LA: onFailure(user): count + 1, lock when it hits the limit
        S->>A: LOGIN_FAILURE
        S-->>FE: 401 invalid_credentials (same message as no such user)
    end

    S->>LA: onSuccess(user): reset the failure count
    S->>A: LOGIN_SUCCESS
    S->>RT: issue(user)
    RT->>DB: insert refresh_tokens row with SHA-256 of a random 32-byte value
    RT-->>S: the raw refresh token (only time it exists in clear)
    S->>AT: issue(user)
    AT->>K: activeKey()
    K-->>AT: RSA private key + kid
    AT->>AT: sign RS256 JWT<br/>sub, roles, iss, aud, iat, exp, jti
    AT-->>S: IssuedAccessToken(token, expiresAt)
    S-->>C: TokenPair(access token, refresh token)
    C-->>FE: 200 {accessToken, tokenType Bearer, expiresIn}<br/>Set-Cookie refresh_token: HttpOnly, Secure, SameSite=Strict, Path=/auth
    FE->>FE: tokenStore.setToken(): kept in memory only
    FE->>C: GET /api/me with Authorization: Bearer
    FE-->>U: shows the ticket queue
```

## What to notice

- **The order of checks is deliberate** (`AuthService.login`): rate limit first (cheap, stops floods), then lockout, then the slow BCrypt password check.
- **Same answer for every failure.** Unknown email, wrong password and deactivated account all return `invalid_credentials`, and the dummy-hash comparison makes them take the same time. An attacker can't use the answer, or its speed, to find out which emails have accounts. The one exception is `account_locked`: a locked-out user needs to know why the right password is refused.
- **Two tokens, two places:**
  - The **access token** (a JWT, short-lived) goes in the JSON body. The browser keeps it in a JavaScript variable only, never in `localStorage`.
  - The **refresh token** (random, long-lived) goes in an `HttpOnly` cookie that JavaScript can't read, and that is only sent to `/auth/*`.
- **API clients are different.** Without the `X-Auth-Client: browser` header (Swagger, curl, Postman), the refresh token comes back in the JSON body instead of a cookie.
- **Every outcome is audited** in `auth_events`, without the password or tokens.
