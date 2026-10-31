# Chess_Engine_App
Trying to make a chess engine while taking in account all the problem solving fundamentals from DSA and also developing a entire webpage like chess.com with matchmaking and anti cheat systems

**Plan:** see [docs/ROADMAP.md](docs/ROADMAP.md) for phases, diagrams and how to verify each step locally.

## Quick start (Windows, Java 21 + Maven)
```bash
mvn verify                    # build + run all tests
engine "position startpos" d  # draw the board   (PowerShell: .\engine "position startpos" d)
engine                        # interactive mode, type 'help'
```

## Where things are
| Folder | What |
|---|---|
| `phase1/` | Board, pieces, FEN, Zobrist hashing (+ `tests/`) |
| `uci/` | The runnable program (UCI commands) |
| `docs/` | Roadmap, phase notes, primers, decisions |

Layout rules: [docs/CODE_LAYOUT.md](docs/CODE_LAYOUT.md)
