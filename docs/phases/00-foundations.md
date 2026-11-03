# Phase 0 — Foundations ✅

**Tag:** `phase-0` (the build was later flattened into phase folders; see ADR-0002)

## What was built
- A single Maven build (`pom.xml`) on Java 21, with JUnit 5 + AssertJ for tests, Spotless
  (google-java-format) for formatting, and GitHub Actions CI (`.github/workflows/ci.yml`) running `mvn verify`.
- `chess_engine_v1/target/chess_engine_v1.jar`: the runnable program (code in `uci/`), plus the `engine.cmd` shortcut.

## Commands
```bash
mvn verify                  # build + all tests + formatting check
mvn spotless:apply          # auto-format all Java code (run before committing)
mvn -q package -DskipTests  # just rebuild chess_engine_v1/target/chess_engine_v1.jar
```
```bash
engine uci isready quit
```
In PowerShell, type `.\engine` instead of `engine`.

Expected output:
```
id name ChessEngineApp 0.1.0
id author NikPihu
uciok
readyok
```
