# Engine v3, step 4: Lazy SMP (multi-threaded search)

**Folder:** [`chess_engine_v3/src/search`](../../chess_engine_v3/src/search) · **Previous:** [step 3, search](v3-03-search.md) · **Plan:** [v3 plan](v3-plan.md)

## The idea
**Splitting the tree is hard:** alpha-beta is sequential by nature. Each move's search uses the bounds the previous moves produced, so cutting the tree into pieces for different cores is complicated and wastes work.

**Lazy SMP** is the simple trick almost every engine uses today. Start N threads that all search **the same position** with **one shared transposition table**, and don't coordinate them at all:
- **They drift apart naturally:** each thread has its own history tables and timing, so the threads search in slightly different orders and reach different parts of the tree first.
- **They share results:** whatever one thread stores in the TT (a cutoff, a best move, a score), the others read. Threads keep finding work already done and skip it.
- **The main thread decides:** thread 0 owns the clock, prints `info` and picks `bestmove`. When it stops, the helpers are told to stop too.

Typical gain: about **+60 Elo per doubling** of threads (published results show 4 threads ≈ +100–130 over 1).

## What was built
| Piece | What |
|---|---|
| `Engine` (`search.h/.cpp`) | Owns the shared TT, the network and N `Search` workers. `think()` starts helpers on `std::thread`s, runs the main search, then stops and joins them |
| `Search` workers | Each has its own position copy, accumulators, killers, histories and correction tables. They share the TT. Helpers have no clock and no depth limit; they run until the main thread signals |
| Two stop signals | UCI `stop` reaches only the main thread. When the main thread returns, a separate flag stops the helpers. A late `stop` can never leave a helper running, and an early one is never lost |
| Node counts | Each worker publishes its node count every 2,048 nodes (atomic, no locking), and the main thread reports the total in `info` |
| TT safety | Entries are checked with key XOR data (step 3), so a torn write by two threads at once just looks like a miss. The hash move is only used if it is in the legal move list |
| UCI | `setoption name Threads value N` (1–256) |

## How to check it
```bash
tools\engine3 test
```
```bash
tools\engine3 "setoption name Threads value 4" "setoption name Hash value 64" "position startpos" "go movetime 3000"
```

**The test** `lazy_smp_threads_search_and_stop` covers four things with 4 threads:
- `go depth` finishes;
- `go infinite` + `stop` returns a move;
- a mate in 1 is still found;
- switching back to 1 thread works.

**Measured** (middlegame, 3 s, Hash 64; an SPRT was using 8 cores at the same time):

| Threads | Depth | Nodes/s |
|---|---|---|
| 1 | 19 | 1.36 M |
| 4 | 21 | 4.52 M |

## Result
| Test | TC | Games | W-L-D | Elo | Result |
|---|---|---|---|---|---|
| v3, 4 threads vs 1 thread (same binary, same network) | 8+0.08 | 220 | 108-6-106 | **+174 ± 32** (nElo +295 ± 46) | ✅ H1 |

**Read it with care:** this is a bit above the usual +100–130 for four threads. Part of the reason: v3's pruning-heavy search benefits from the shared TT, and the test ran while the machine was otherwise busy. The point stands: on Lichess, the bot should use 4 threads.

## On Lichess (when you decide to switch)
- **The plan's setting:** 4 threads + 256 MB hash, which leaves 12 of 16 hardware threads for training.
- **Nothing is switched automatically.** The bot stays on v2-gen-8 until you say so.
