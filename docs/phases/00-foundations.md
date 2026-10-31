# Phase 0 — Foundations ✅

**Tag:** `phase-0`

## What was built
- A Maven multi-module build: the parent `pom.xml` plus the `engine` and `engine-uci` modules, on Java 21.
- JUnit 5 and AssertJ for tests, Spotless (google-java-format) for formatting, and a GitHub Actions CI
  (`.github/workflows/ci.yml`) that runs `mvn verify`.
- `engine-uci.jar`: a runnable jar that answers the UCI handshake.

## Commands
```bash
mvn verify                  # build + all tests + formatting check
mvn spotless:apply          # auto-format all Java code (run before committing)
java -jar engine-uci/target/engine-uci.jar uci isready quit
```

Expected output of the last command:
```
id name ChessEngineApp 0.1.0
id author NikPihu
uciok
readyok
```
