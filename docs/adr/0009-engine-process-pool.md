# ADR-0009 - The C++ engine runs as a pool of UCI processes

- **Status:** accepted
- **Date:** 2026-11-09

## Context
The strongest engine (`chess_engine_v3`) is C++. The server is Java. Computer games, analysis, reviews and fair-play checks all need it.

## Decision
- **Talk UCI to a small pool of warm engine processes** (`EnginePool`).
- **Back-pressure:** callers wait up to 10 s for a free engine, then get HTTP 503.
- **Fallback:** if the binary is missing, the Java engine from `chess_engine_v1` is used instead.
- **Legality:** move legality always comes from the v1 Java rules; the engine only *thinks*.

## Consequences
- No JNI: an engine crash cannot take the server down; the process is simply replaced.
- **The pool size is the capacity knob:** 1 on a t3.micro, more on bigger machines.
- The Docker image compiles the engine for Linux with `-march=haswell` (AVX2 + BMI2).
