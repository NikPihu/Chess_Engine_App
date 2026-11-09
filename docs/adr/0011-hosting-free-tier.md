# ADR-0011 - Start on one AWS free-tier instance

- **Status:** accepted
- **Date:** 2026-11-09

## Context
Budget: about $0 to start. The engine needs x86 BMI2 (PEXT), so Graviton (ARM) instances are out.

## Decision
- **One EC2 t3.micro** running Docker Compose: Caddy (HTTPS + static files), the server with its engine, and optionally Postgres and Redis.
- **Databases can move** to the Neon and Upstash free tiers to save RAM.
- **Everything is configured** through `deploy/.env`.

## Consequences
- **Capacity:** a few dozen concurrent players, with one full-strength engine search at a time.
- **First upgrade:** a t3.small (about $15/month), then raise `ENGINE_POOL_SIZE`.
- The same images run unchanged on any VPS (Oracle, Hetzner...) if needed.
