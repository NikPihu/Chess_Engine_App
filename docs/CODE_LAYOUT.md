# Code layout rules

## One folder per engine, shared tools outside
```
chess_engine/
├─ pom.xml               builds every engine (root aggregator)
├─ engine.cmd            run an engine:  engine "position startpos" d   /   engine gui
│
├─ chess_engine_v1/      ENGINE v1: alpha-beta + PeSTO (everything built in Phases 1–6)
│   ├─ pom.xml           its own build → chess_engine_v1/target/chess_engine_v1.jar
│   ├─ phase1/ … phase6/ engine code, one flat folder per phase (+ tests/ inside)
│   ├─ uci/              its UCI front door (UciMain, UciSession, Engines registry)
│   └─ gui/              its browser board (engine gui)
│
├─ chess_engine_v2/      (next) e.g. NNUE engine: own folder, own pom.xml, own jar
│
├─ tools/                SHARED, works with any engine that speaks UCI
│   ├─ Sprt.java, Ladder.java   SPRT testing lab (fastchess)
│   ├─ bot.cmd                  run an engine on Lichess (lichess-bot)
│   └─ bin/ books/ versions/ results/ lichess-bot/   git-ignored downloads and output
└─ docs/                 roadmap, phase notes, primers, decisions, Elo ledger
```

## Why this split
- **The contract between engines and tools is UCI,** the standard chess-engine protocol. The SPRT lab,
  the Lichess bot, Cute Chess, fastchess and Stockfish all talk UCI, so any engine folder that builds a
  UCI program plugs in without changing the tools. It can even be written in another language.
- **`uci/` and `gui/` live inside the engine,** because they use that engine's own board and move
  generator. A new engine can copy them as a starting point, or bring its own.
- **Choosing which engine the tools run:** set `ENGINE_JAR` before running a launcher.
  The default is `chess_engine_v1\target\chess_engine_v1.jar`.
  ```bash
  set ENGINE_JAR=chess_engine_v2\target\chess_engine_v2.jar
  ```
  ```bash
  tools\bot
  ```
  For the lab, use `java tools/Sprt.java ... --jar <path>`.

## Rules inside an engine folder
1. **One concept per file.** Each file starts with a comment explaining its idea.
2. **A phase folder is frozen once its phase is tagged.** It only gets bug fixes, each with a test
   that reproduces the bug.
3. **Later phases build on earlier folders, never edit them.** `phase6/` imports `phase4.Search` and
   extends it. Improvements go into the newer folder.
4. **Old code keeps its tests running.** Newer code is tested against older code; for example, the
   magic move generator must match the simple ray generator.
5. **Dependencies only point backwards.** `phase2` may use `phase1`, never the other way round.

## How the build finds the code
Each engine folder is its own Java source root, so inside `chess_engine_v1/` the folder name is the
package name (`chess_engine_v1/phase1/Board.java` is `package phase1;`). Tests are compiled along with
the code. JUnit is only available at build time, and test classes are left out of the jar.

A new engine should use a distinct package prefix (e.g. `v2.search`) so two engines never define the
same class name if one ever reuses the other's code.
