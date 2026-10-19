# 4.5 Signing keys: startup, rotation and JWKS

[← 4.4 Page reload and logout](04-reload-and-logout.md) · [Sequence list](README.md) · Next: [4.6 Inbound email →](06-inbound-email.md)

Every access token is signed with an RSA private key. This is where those keys come from, how they're replaced, and how anyone can get the public halves.

## At startup: make sure a key exists

```mermaid
sequenceDiagram
    autonumber
    participant APP as Spring Boot startup
    participant B as SigningKeyBootstrap
    participant K as SigningKeyService
    participant E as KeyEncryptionService
    participant DB as Postgres

    APP->>B: run() (before logins are accepted)
    B->>K: ensureActiveKey()
    K->>DB: find the ACTIVE key
    alt one exists
        DB-->>K: it (nothing to do)
    else none (first start)
        K->>K: generate RSA 2048 key pair, kid = random UUID
        K->>E: encrypt(private key)
        E-->>K: AES-256-GCM ciphertext (key = HELPDESK_KEK env var)
        K->>DB: insert signing_keys (public JWK, encrypted private, ACTIVE)
        Note over K,DB: Two instances starting together? The unique index allows<br/>one ACTIVE row, the loser reads the winner's key.
    end
```

## Every 7 days: rotation

```mermaid
sequenceDiagram
    autonumber
    participant SCH as db-scheduler
    participant T as KeyRotationTask
    participant K as SigningKeyService
    participant DB as Postgres
    participant A as AuditService

    SCH->>SCH: key-rotation job is due<br/>(exactly one app instance picks it)
    SCH->>T: execute
    T->>K: rotate()
    K->>DB: lock the ACTIVE key
    K->>DB: set it RETIRED, retired_at = now
    K->>DB: insert a brand-new ACTIVE key
    T->>A: KEY_ROTATED
    Note over K: New tokens use the new kid. Tokens signed with<br/>the old key still verify until they expire.
```

## Anyone can fetch the public keys: JWKS

```mermaid
sequenceDiagram
    autonumber
    participant X as Any client<br/>(another service, a gateway)
    participant J as JwksController
    participant K as SigningKeyService

    X->>J: GET /.well-known/jwks.json (no login needed)
    J->>K: publishedKeys()
    K-->>J: the ACTIVE key + RETIRED keys retired<br/>less than (access-token TTL + buffer) ago
    J->>J: strip private parts (toPublicJWK)
    J-->>X: 200 {"keys": [...]}, Cache-Control max-age 5 min
    X->>X: verify a JWT: find the key whose kid matches, check the signature
```

## What to notice

- **Why keep retired keys published for a while?** A token signed one minute before rotation is still valid for the rest of its lifetime. Its key must stay in JWKS until that token has expired, then it's dropped.
- **Private keys are never stored in clear.** They're encrypted with a key-encryption key (`HELPDESK_KEK`) that lives in an environment variable, not in the database. Stealing a database backup isn't enough to forge tokens.
- **The `kid`** (key ID) in each token's header is how a verifier knows which public key to use. That's what makes rotation possible without breaking existing tokens.
- **This is the same model OCI IAM and other identity providers use:** a JWKS URL, rotating keys, and `kid` selection.
