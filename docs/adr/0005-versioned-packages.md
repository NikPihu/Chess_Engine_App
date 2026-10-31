# ADR-0005 — Versioned packages, one concept per file

- **Status:** accepted
- **Date:** 2026-10-31

## Context
This is a learning project. Each improvement (better move generation, better search) should be
understandable on its own, and earlier code must stay provably correct when later code arrives.

## Decision
- One major concept per file.
- Engine components live in versioned packages (`board/v1`, `movegen/v2`, …). A new version goes
  next to the old one instead of overwriting it, and old versions keep their tests in CI.
  The full rules are in [CODE_LAYOUT.md](../CODE_LAYOUT.md).

## Consequences
- **Easier:** you can read a single version in isolation and diff two versions folder to folder.
  Old versions double as test oracles, e.g. `movegen/v2` must equal `movegen/v1` on thousands of positions.
- **Harder:** there is some duplicated code between versions, and every new version has to decide
  which versions of other components it builds on.
- **Watch:** only version components that really change. Never copy code just to bump a number.

## Alternatives considered
- **Git tags only:** history is safe, but you can't run old and new code side by side or compare them in one test.
- **Feature flags inside one class:** gets hard to read quickly, which defeats the learning goal.
