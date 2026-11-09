# ADR-0004 — Session-cookie auth over JWT

- **Status:** accepted (implemented in gambyte-server, 2026-11-09)
- **Date:** 2026-10-31

## Context
We need a single web app (SPA plus API on the same domain behind Caddy) with WebSocket connections that must be authenticated.

## Decision
Use Spring Security **server-side sessions** (HttpOnly, Secure, SameSite cookies) with CSRF protection.
Sessions are stored in Redis (Spring Session) so they survive restarts and scale to more than one instance.
The WebSocket handshake reuses the same session.

## Consequences
- Logout and revocation are trivial: delete the session.
- Tokens never sit in `localStorage`, which removes an XSS token-theft vector.
- CSRF handling is required. Spring provides it.

## Alternatives considered
- **JWT:** better for separate domains or mobile clients, but revocation is hard and it adds complexity we don't need yet.
  We'll revisit it if the arena or mobile clients need tokens.
