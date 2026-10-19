# 4.3 Token refresh and reuse detection

[← 4.2 An authenticated API call](02-authenticated-request.md) · [Sequence list](README.md) · Next: [4.4 Page reload and logout →](04-reload-and-logout.md)

Access tokens expire quickly (minutes). When one does, the web app quietly swaps the refresh cookie for a new pair of tokens and retries. Endpoint: `POST /auth/refresh`.

```mermaid
sequenceDiagram
    autonumber
    participant FE as Web app<br/>client.ts
    participant C as AuthController
    participant S as AuthService
    participant RT as RefreshTokenService
    participant DB as Postgres
    participant AT as AccessTokenIssuer
    participant A as AuditService

    FE->>C: GET /api/tickets (access token has expired)
    C-->>FE: 401
    Note over FE: refreshSession(): if several requests got 401<br/>at once, they all share ONE refresh call
    FE->>C: POST /auth/refresh<br/>cookie refresh_token + X-Auth-Client: browser
    C->>C: refreshTokenFrom(): body wins, else the cookie<br/>(the cookie counts only with the browser header)
    C->>S: refresh(raw token, ip)
    S->>RT: rotate(raw, ip)
    RT->>DB: SELECT ... FOR UPDATE by SHA-256(raw)<br/>(row lock: two refreshes can't both win)

    alt no row with that hash
        RT-->>FE: 401 invalid_refresh_token
    else row already revoked: this token was used before
        Note over RT: Someone is replaying an old token.<br/>Either the user or a thief has the newer one.
        RT->>DB: revoke EVERY refresh token of this user
        RT->>A: TOKEN_REUSE_DETECTED
        RT-->>FE: 401 (the revocation still commits)
    else expired
        RT-->>FE: 401
    else user deactivated
        RT->>DB: revoke this token
        RT-->>FE: 401
    end

    RT->>DB: insert a new refresh token (hash only)
    RT->>DB: old token: revoked_at = now, replaced_by_id = new id
    RT-->>S: Rotation(user, new raw token)
    S->>A: TOKEN_REFRESH
    S->>AT: issue(user)
    AT-->>S: new access token
    S-->>C: TokenPair
    C-->>FE: 200 {accessToken} + Set-Cookie with the NEW refresh token
    FE->>FE: setToken(new access token)
    FE->>C: retry GET /api/tickets once, with the new token
    C-->>FE: 200

    Note over FE: If the refresh failed, or the retry is 401 again:<br/>clearToken(), back to the sign-in page
```

## What to notice

- **Rotation:** every refresh token works exactly once. Using it gives you a new one and marks the old one `revoked`, with `replaced_by_id` pointing at its successor.
- **Reuse detection:** if a revoked token is ever presented again, someone copied it. HelpDesk can't tell whether the thief or the real user is replaying it, so it ends *all* of that user's sessions. Both must log in again, and the thief's copy is now useless.
- **Why the web app shares one refresh:** if two requests each refreshed with the same cookie, the second would look like reuse and sign the user out everywhere. `refreshSession()` keeps one refresh in flight and makes every caller wait for it.
- **Why the row lock:** the same protection on the server side. Two refreshes arriving at the same moment are processed one after the other, so exactly one succeeds.
- **The cookie needs a custom header too.** A malicious site can make a browser send the cookie, but it can't add `X-Auth-Client: browser`. Together with `SameSite=Strict`, that blocks cross-site request forgery on `/auth/refresh`.
