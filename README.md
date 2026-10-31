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
| `phase1/` | Board, pieces, FEN, Zobrist hashing (+ `tests/`) |
| `phase2/` | Move generation, magic bitboards, make/unmake, perft |
| `phase3/` | Game rules, SAN/PGN, search interface, random player |
| `phase4/` | Search v1 (alpha-beta, TT, PeSTO eval) |
| `phase6/` | Search v2 + evaluation v2 (feature switches) |
| `uci/` | The runnable program (UCI commands, engine registry) |
| `gui/` | Graphical board in the browser (`engine gui`) |
| `tools/` | SPRT testing (`Sprt.java`, `Ladder.java`), lichess bot launcher |
| `docs/` | Roadmap, phase notes, primers, decisions |

Layout rules: [docs/CODE_LAYOUT.md](docs/CODE_LAYOUT.md)
