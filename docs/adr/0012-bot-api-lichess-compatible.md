# ADR-0012 - A Lichess-compatible Bot API instead of hosting uploaded bots

- **Status:** accepted
- **Date:** 2026-11-09

## Context
"Bot models can be hosted" was a requirement. Running strangers' executables on our server is unsafe (arbitrary code) and expensive (CPU).

## Decision
- **Bots connect from their owners' machines** with an API token, using the same endpoints and NDJSON stream format as Lichess.
- **Our own engine** is hosted (it is ours and built from source) and plays as GambyteEngine.

## Consequences
- Existing bot software (`lichess-bot`) works with only a URL change.
- No sandboxing problem, and the CPU cost is the bot owner's.
