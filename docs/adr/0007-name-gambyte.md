# ADR-0007 - The product is called Gambyte

- **Status:** accepted
- **Date:** 2026-11-09

## Context
The website needed a name that is short, chess-flavoured, not cringe, and free to use as a brand.

## Decision
**Gambyte** (gambit + byte). The built-in engine plays as the bot account **GambyteEngine**, at levels 1-8.

## Consequences
- Code lives in `gambyte-server` / `gambyte-web`, and the Java package is `com.gambyte`.
- The engine projects keep their historical names (`chess_engine_v1..v3`).
