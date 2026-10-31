# ADR-0002 — Maven multi-module monorepo

- **Status:** accepted
- **Date:** 2026-10-31

## Context
Engine, UCI wrapper, server, arena, tools and web all evolve together, and engine changes must be tested
against their users in the same commit.

## Decision
Use one Git repo with a Maven parent `pom.xml` and the modules `engine`, `engine-uci`, `server` and `arena`.
`web/` is a separate npm project inside the same repo.
Each module is added in the phase that needs it, not up front.

## Consequences
- One PR can change the engine and its callers together, and CI builds everything.
- The `engine` module must stay dependency-free so it's trivial to embed anywhere.

## Alternatives considered
- **Separate repos per component:** version skew between them, and more ceremony than a solo/learning project needs.
