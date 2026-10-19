# 4. Sequence diagrams: how requests move

[← 3. Data model](../03-data-model/README.md) · [All diagrams](../README.md) · Next: [5. State machines →](../05-state-machines/README.md)

Each file follows one action from start to finish, naming the real classes and methods. Read them in this order:

| # | Flow | Why at this point |
|---|---|---|
| 1 | [Login](01-login.md) | Everything else needs a signed-in user. |
| 2 | [An authenticated API call](02-authenticated-request.md) | What happens to the access token on every request. |
| 3 | [Token refresh and reuse detection](03-token-refresh.md) | How a session lasts longer than one access token. |
| 4 | [Page reload, logout and ending sessions](04-reload-and-logout.md) | How sessions start again and how they end. |
| 5 | [Signing keys: startup, rotation, JWKS](05-signing-keys.md) | Where the keys behind every token come from. |
| 6 | [A student's email becomes a ticket](06-inbound-email.md) | The start of the main business flow. |
| 7 | [The AI processes the ticket](07-ai-processing.md) | The background job right after step 6. |
| 8 | [An agent claims and replies; the email goes out](08-agent-reply-and-email.md) | The human side, and the email outbox. |
| 9 | [Auto-close](09-auto-close.md) | The end of a ticket's life. |

Flows 1–5 are the identity and access part (the core of the IAM interview topics). Flows 6–9 are the support-ticket part.

**Reading tip:** read each diagram once top to bottom for the happy path, ignoring the `alt` boxes. Then read it again for the `alt` branches: those are the error cases and security checks.
