# ADR-0002 — One repo, one build, flat phase folders

- **Status:** accepted (revised 2026-10-31; first version used Maven's standard multi-module layout)
- **Date:** 2026-10-31

## Context
The first layout followed the Maven standard (`engine/src/main/java/io/github/nikpihu/chess/engine/board/v1/Board.java`).
That's nine folders before reaching a class, which made the code hard to browse for a learning project.

## Decision
One Git repo with a single `pom.xml`. The project folder is the Java source root, and code lives in
one flat folder per phase (`phase1/`, `phase2/`, …) with a `tests/` folder inside each.
`uci/` holds the runnable program. Full rules: [CODE_LAYOUT.md](../CODE_LAYOUT.md).

Product phases (the Spring Boot server and React web app) may add their own build file inside
their phase folder when they arrive. That decision is made at the start of Phase 8.

**Revision (2026-11-03):** all engine code moved into `chess_engine_v1/`, which has its own
`pom.xml`. A root `pom.xml` builds every engine. Shared tools (`tools/`, `engine.cmd`, the Lichess
bot) only talk UCI, so future engines (`chess_engine_v2/`, …) plug in unchanged. See
[CODE_LAYOUT.md](../CODE_LAYOUT.md).

## Consequences
- **Easier:** every class is at most two folders deep, and the folder name is the package name.
- **Harder:** this isn't the Maven default, so `pom.xml` needs custom `sourceDirectory`, compiler
  `includes`, and a surefire test location. JUnit is a "provided" dependency rather than "test",
  so main code *could* import it by mistake. We rely on review to keep it out.
- Phase direction (`phase2` may use `phase1`, never the reverse) is a convention, not something
  the build checks.

## Alternatives considered
- **Standard Maven layout:** IDE-perfect, but deeply nested. Rejected by the project owner.
- **One Maven module per phase:** enforces direction, but every phase folder needs its own
  `src/main/java/...` and `pom.xml`, which brings the nesting back.
