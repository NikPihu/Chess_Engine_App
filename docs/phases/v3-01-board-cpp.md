# Engine v3, step 1: the C++ board and move generator

**Folder:** [`chess_engine_v3/`](../../chess_engine_v3) · **Plan:** C++ engine, Python trainer, Java app (see "How the code fits together" below)

## Why C++, and what stays in Java/Python

**What moves to C++:** everything that runs *inside the engine process*. That's the board, move generation, search, NNUE evaluation, UCI and self-play data generation. The result is one program, `chess_engine_v3/bin/engine_v3.exe`.

**Why not mix languages inside one engine:** the search calls move generation and evaluation millions of times per second. Every Java↔C++ crossing (JNI) costs time and blocks inlining, so a half-Java, half-C++ engine would be slower than either pure one.

**What stays put:** the split happens at the process boundary, where engines already talk **UCI** (text over stdin/stdout).
- **Python** keeps training and running the self-improvement loop.
- **Java** keeps the app side: the GUI and match tools from v1, and the planned Spring Boot backend.
- **The shared tools** (fastchess SPRT, lichess-bot) run whichever engine binary they are pointed at.

**v1 and v2 stay in the repo as the oracle.** v3 must reproduce their perft numbers, and later v2's network outputs.

## What was built

| File | What |
|---|---|
| `src/core/types.h` | Colours, pieces, squares (a1 = 0 … h8 = 63) and the 16-bit move encoding. All identical to v1, so v2's data and networks carry over |
| `src/core/bitboard.*` | Bit tricks and attack tables. Leapers use 64-entry tables. Sliders use **PEXT tables**; `between` / `line` hold the squares between and through two squares |
| `src/board/position.*` | Bitboards + mailbox, FEN, make/unmake with a state stack, incremental Zobrist key (+ a pawn/king key for later), repetition / 50-move / bare-king draws |
| `src/movegen/movegen.*` | **Fully legal** generation (`ALL`, `NOISY` = captures + promotions, `QUIET`) |
| `src/movegen/perft.*` | Perft with bulk counting, and divide |
| `src/uci/uci.*` | `uci`, `isready`, `position`, `go perft N`, `perft N`, `d`, `bench`, `quit` (search comes in step 2) |
| `tests/tests.cpp` | Self-contained test runner (no framework to install) |

## The ideas, and how they differ from v1

### PEXT sliders instead of magic numbers
- **What determines a rook's attacks:** only the pieces on its "mask", the rays without their last square. That's at most 12 squares.
- **The PEXT instruction (BMI2)** gathers exactly those occupancy bits into a dense number, 0 … 4095.
- **That number indexes a pre-computed table of attack sets.**
- **Compared with v1's magics:** v1 did the same job by multiplying with a searched-for magic number and shifting. PEXT does it in one instruction, with no magic search and no collisions.
- **Table size:** 102,400 rook entries and 5,248 bishop entries, built in about a millisecond at start-up.
- **CPU requirement:** BMI2, which is in every Intel CPU since 2013 and AMD since Zen. The engine checks for it at start-up.

### Legal moves directly (v1 played every move to test it)
v1 generated *pseudo-legal* moves and rejected those that left the king in check. v3 uses three facts to emit only legal moves:
1. **In double check** only the king can move.
2. **In single check** every other move must capture the checker or block. The **check mask** is `between[king][checker] | checker`.
3. **A pinned piece** (alone between our king and an enemy slider) may only move along `line[king][piece]`.

Three special cases:
- **King moves** are checked with the king *removed* from the board. Otherwise a king in check from a rook could step "backwards" along the rook's ray, onto a square the king itself was hiding.
- **En passant** removes two pawns from the same rank, so it can expose the king sideways. It is verified by simulating the capture on the bitboards.
- **Castling** checks that the king is not in check and doesn't pass through or land on an attacked square.

### En passant only when possible
The en passant square is recorded (and hashed) **only if an enemy pawn can actually capture there**. Otherwise two identical positions would get different keys, and repetitions would be missed.

### Fixed Zobrist keys
- **Fixed seed:** the keys come from splitmix64 with a fixed seed, so bench node counts are reproducible.
- **Castling keys** are XORs of four per-right keys, so any rights change is a single XOR.

## How to check it
```bash
tools\engine3 test
```
```bash
tools\engine3 bench
```
```bash
tools\engine3 "position fen r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1" "perft 4"
```

**What the tests prove:**
- **Perft:** all **21 perft positions from v1's `PerftTest`** give identical counts, plus depth-6 perft from the start position (119,060,324). The FEN and key are restored after each run.
- **Random games:** 420 random games of up to 150 plies (20 from each perft position). At every step:
  - the incremental key equals the key recomputed from scratch;
  - `NOISY` + `QUIET` = `ALL`;
  - re-reading the printed FEN gives the same key.
- **The rest:** slider tables against a slow ray walk (20k random boards), repetition / 50-move / bare-king draws, and UCI `position … moves`.

**Speed (perft with bulk counting, i5-13500H, one thread):**

| | nodes/second |
|---|---|
| v1 (Java, after JIT warm-up) | 98 M |
| v3 (C++, MSVC /O2 /arch:AVX2) | 330–355 M |

This is move generation alone. Search speed (with NNUE) is measured in step 2, where v3.0 plays v2-gen-8 with the same network.

## Next: step 2 = v3.0, a faithful port of v2
- **The search:** UCI search with the same 7 SPRT-proven features and the same time manager.
- **The network:** NNUE inference that reads `chess_engine_v2/nets/gen-8.nnue` and matches `gen-8.golden.txt`.
- **The gate:** SPRT v3.0 vs v2-gen-8 shows what C++ alone is worth.
