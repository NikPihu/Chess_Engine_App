# Gambyte API

- **REST endpoints:** the full list, always current, is the OpenAPI document at `/v3/api-docs`, which you can browse at `/swagger-ui.html`.
- **This page covers what Swagger cannot show:** authentication, the WebSocket protocol and the bot streams.

## Authentication

| Client | How |
|---|---|
| Browser | `POST /api/auth/login` (or `/register`) sets the `GAMBYTE_SESSION` cookie. Every non-GET request must send the `XSRF-TOKEN` cookie's value as the `X-XSRF-TOKEN` header. `GET /api/auth/csrf` creates the cookie. |
| Bot or script | `Authorization: Bearer gbt_...` (create tokens on the Settings page). No cookies and no CSRF needed. |

**Errors** are RFC 9457 problem details with a machine-readable `code`:
```json
{"status": 409, "detail": "That username is taken", "code": "username_taken"}
```
HTTP 402 with `premium_required` means a paid feature was used by a free account.

## WebSocket (STOMP)

Connect to `wss://<host>/ws` with the session cookie.

| Destination | Direction | Payload |
|---|---|---|
| `/topic/game/{id}` | server → all watching | `GameEvent`: `move`, `end`, `draw`, `rematch`, `chat`, `state` (refetch) |
| `/user/queue/match` | server → you | `{gameId}`: a pairing, accepted challenge or arena game started |
| `/user/queue/challenge` | server → you | an incoming challenge |
| `/user/queue/notify` | server → you | a notification |
| `/user/queue/errors` | server → you | `{code, message}`: a rejected socket message |
| `/app/game/{id}/move` | you → server | `{uci, lag}` |
| `/app/game/{id}/blur` | you → server | tab lost focus (fair-play signal) |
| `/app/ping` | you → server | presence heartbeat, every 20 s |

- **Versions:** every game event has a version `v`. If you see a gap, call `GET /api/games/{id}`; that full snapshot is also what you load after a reconnect.
- **Clock values:** clocks are milliseconds with the server's timestamp. Count down locally from those numbers.

## Bot API (Lichess-compatible)

The shapes follow Lichess, so `lichess-bot` works with `url: https://<host>/` and a Gambyte token.

| Endpoint | Purpose |
|---|---|
| `GET /api/account` | the token's account |
| `POST /api/bot/account/upgrade` | make this (game-less) account a BOT |
| `GET /api/stream/event` | NDJSON: `challenge`, `gameStart`, `gameFinish`, `challengeDeclined`, `challengeCanceled` |
| `GET /api/bot/game/stream/{id}` | NDJSON: `gameFull` first, then `gameState` after each change, `chatLine` |
| `POST /api/bot/game/{id}/move/{uci}` | play a move |
| `POST /api/bot/game/{id}/resign` / `abort` / `chat` | — |
| `POST /api/challenge/{username}` | challenge someone (form fields `clock.limit`, `clock.increment`, `rated`, `color`) |
| `POST /api/challenge/{id}/accept` / `decline` | answer a challenge |

Streams send an empty line every 6 s as a keep-alive. Challenging `GambyteEngine` starts a game at once.
