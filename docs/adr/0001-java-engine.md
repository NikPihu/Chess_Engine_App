# ADR-0001 — Java 21 for the chess engine

- **Status:** accepted
- **Date:** 2026-10-31

## Context
The backend is Spring Boot (Java). The engine must be used in three places: a UCI binary for GUIs,
fastchess and lichess-bot; inside the server, for move validation, "play vs computer" and fair-play analysis;
and inside the LLM Arena, for validation and scoring.

## Decision
Write the engine in **Java 21** as dependency-free code in the phase folders, with a thin UCI program (`uci/`, built as `chess_engine_v1/target/chess_engine_v1.jar`).

## Consequences
- **Easier:**
  - One language, one build tool (Maven) and one test framework.
  - The server calls the engine directly instead of managing UCI subprocesses.
  - Java 21 is already installed.
- **Harder:**
  - Raw speed is roughly 1.5–2× below well-written C++.
  - We must keep the hot path allocation-free (primitive `int`/`long`, reusable arrays, no boxing,
    no streams) and warm up the JIT before timed searches.
- **Watch:** track `bench` nodes/sec in the ledger so performance regressions are visible.

## Alternatives considered
- **C++:** fastest, but the local g++ is 6.3 (2016), and the server would need a process bridge.
- **Rust:** fast and safe, but adds a second toolchain and language to learn alongside Spring.
