# Gambyte - security checklist

How Gambyte handles the OWASP Top 10 and other common risks. Check this list when adding features.

| Risk | How Gambyte handles it | Where |
|---|---|---|
| Broken access control | Every endpoint needs a login unless explicitly public. Game actions check that the user plays that game. `/api/admin/**` needs MOD; money and role changes need ADMIN (`@PreAuthorize`). | `SecurityConfig`, `GameService.colourOrThrow`, `AdminController` |
| Session theft | HttpOnly, SameSite=Lax cookies; Secure in production. The session id changes at login. Sessions live in Redis and logout deletes them. | `application*.yml`, `AuthController.startSession` |
| CSRF | Cookie-to-header token on every state-changing request. Token-authenticated (bot) calls carry no cookies, so they are exempt. WebSocket origins are restricted. | `SecurityConfig`, `lib/api.ts`, `WebSocketConfig` |
| Password storage | Argon2id (memory-hard). The delegating encoder allows future upgrades. | `SecurityConfig.passwordEncoder` |
| Brute force and spam | Token-bucket limits per IP and per account: login, register, password reset, moves, chat, challenges, analysis, reports, coach. | `RateLimiter` and its callers |
| Account enumeration | "Forgot password" answers the same whether or not the email exists. | `UserService.forgotPassword` |
| Token leaks | API tokens and email-link tokens are stored only as SHA-256 hashes. API tokens are shown once and are revocable. | `ApiTokenService`, `UserService` |
| Injection | JPA and parameterised `JdbcTemplate` everywhere, with no string-built SQL from input. Bean Validation on request bodies. | repositories, `*Dtos` |
| XSS | React escapes all output. A strict CSP (scripts only from self and Razorpay), `nosniff`. Chat strips links and masks slurs. | `Caddyfile`, `ChatFilter` |
| Clickjacking | `frame-ancestors 'none'`. | `Caddyfile`, `SecurityConfig` |
| Payment fraud | Checkout and webhook signatures are verified with HMAC-SHA256 using constant-time comparison. Webhooks are idempotent through stored event ids. Card/UPI data never touches our servers. | `RazorpayGateway`, `PremiumService` |
| Cheating | The server is the only authority on moves and clocks. Lag compensation is capped at 300 ms. Fair-play statistics produce flags for human review, never automatic bans. | `ChessClock`, `FairPlayService` |
| Secrets | Secrets come only from environment variables (`deploy/.env` is git-ignored); nothing is hard-coded. There is no default admin; the first admin is named by env var. | `.env.example`, `AdminBootstrap` |
| Running untrusted code | Bot owners run their programs on their own machines; Gambyte never executes uploaded code. The engine binary is built from this repository. | `BotApiController` |
| Errors leaking internals | Unexpected exceptions produce a generic 500, with details only in the server log. | `GlobalExceptionHandler` |
| Vulnerable dependencies | Dependabot opens weekly PRs; CI builds and tests every change. | `.github/dependabot.yml`, `ci.yml` |
| Transport | HTTPS everywhere (Caddy, automatic certificates) with HSTS. | `Caddyfile` |
| Denial of service | Bounded engine pool returns 503 when busy. Request size limits. Container memory limits. | `EnginePool`, `docker-compose.prod.yml` |
| Logging and audit | Moderator actions go to the `gambyte.audit` log. JSON logs in production. | `AdminController` |

**Known limits** (fine for one server, revisit before scaling out):
- Rate-limit buckets live in memory; move them to Redis (Bucket4j has an integration) when there is more than one server.
- The STOMP broker is in-memory; use a broker relay for several servers.
