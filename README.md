# Chess_Engine_App
Trying to make a chess engine while taking in account all the problem solving fundamentals from DSA and also developing a entire webpage like chess.com with matchmaking and anti cheat systems

**Plan:** see [docs/ROADMAP.md](docs/ROADMAP.md) for phases, diagrams and how to verify each step locally.

## Quick start (Windows, Java 21 + Maven)
```bash
mvn verify                    # build + run all tests
engine gui                    # chess board in your browser - play the engine (PowerShell: .\engine gui)
engine "position startpos" d  # draw the board in the terminal
engine "go movetime 2000"     # let the engine think 2 s from the start position
engine                        # interactive UCI mode, type 'help'
```

How to check every phase: see the phase notes in [docs/phases/](docs/phases/).

## Where things are
| Folder | What |
|---|---|
| `chess_engine_v1/` | **Engine v1** (alpha-beta + PeSTO, Phases 1–6): `phase1/` … `phase6/`, its `uci/` program and `gui/` board |
| `chess_engine_v2/` | **Engine v2**: v1's search + a learned **NNUE** evaluation, improved by self-play + Lichess games ([notes](docs/phases/v2-nnue.md)) |
| `tools/` | **Shared by every engine:** SPRT lab (`Sprt.java`, `Ladder.java`), Lichess bot launcher (`bot.cmd`) |
| `docs/` | Roadmap, phase notes, primers, decisions, Elo ledger |

Layout rules: [docs/CODE_LAYOUT.md](docs/CODE_LAYOUT.md)
