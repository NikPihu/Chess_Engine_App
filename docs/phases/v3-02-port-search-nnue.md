# Engine v3, step 2: v3.0 = v2's search and network in C++

**Folder:** [`chess_engine_v3/`](../../chess_engine_v3) · **Previous:** [step 1, board](v3-01-board-cpp.md) · **Plan:** [v3 plan](v3-plan.md)

**Goal of this step:** change *only the language*. The search, the evaluation and the time management are the same as v2's. The SPRT result therefore measures what C++ alone is worth, and every later step starts from a known-good base.

## What was built

| File | What | Ported from |
|---|---|---|
| `src/nnue/nnue.*` | Loads v2's `.nnue` files (or the network built into the exe), accumulator refresh + incremental update, SCReLU output, all AVX2 | `chess_engine_v2/nnue/{Network,Accumulator,NetFile,VectorKernels}` |
| `src/search/tt.*` | Transposition table: same packing, same replacement rule, mate scores stored relative to the node | `chess_engine_v1/phase4/TranspositionTable` |
| `src/search/search.*` | Iterative deepening, aspiration windows, PVS, null move, reverse futility, LMR (same table), killers, history, qsearch. Also the `Features` bit switches and the time manager | `chess_engine_v2/search/NnueSearch`, `chess_engine_v1/phase4/{Search,TimeManager}`, `phase6/{MoveOrdering2,Killers,History}` |
| `src/uci/uci.*` | Search on a background thread (`stop` and `isready` work while thinking). Options `Hash`, `EvalFile`, `Move Overhead`, `Features`. Commands `eval`, `bench [depth]`, `benchperft` | `chess_engine_v1/uci/UciSession` |
| `cmake/embed.cmake` | Builds `chess_engine_v2/nets/best.nnue` into the exe (like v2's jar bundles it). `EvalFile` overrides it | — |
| `tools/Sprt.java` | Accepts an `.exe` as `--jar` / `--base-jar`, so C++ engines can be SPRT-tested | — |

## The ideas worth knowing

### The integer maths must match bit for bit
- **Why it matters:** the trainer (Python) writes a *golden file* with each network: 200 positions and the integer evaluation it computed. If the engine computes anything else, it isn't playing the network that was trained.
- **How the C++ version computes the same numbers with AVX2 (16 values per instruction):**
  - `clamp(acc, 0, 255)` uses `max`/`min` on 16-bit lanes.
  - `c × w` fits in 16 bits (|c| ≤ 255, |w| ≤ 127, because training clips weights to ±1.98 and output weights are ×64). So `mullo_epi16(c, w)` is exact.
  - `madd_epi16(c·w, c)` multiplies by `c` again and adds neighbouring pairs into 32-bit sums.
  - The total is the same integer sum v2's Java loop computes, so the divisions afterwards give the same result.
- **Accumulator updates** use wrapping 16-bit adds (`add_epi16` / `sub_epi16`), which is exactly what Java's `(short)` casts do.

### Search on its own thread
- **Why:** UCI expects the engine to keep listening while it thinks. A GUI or lichess-bot may send `stop`, or `isready` to check the engine is alive.
- **How:** `go` starts a `std::thread`. `stop` sets an atomic flag that the search checks every 2,048 nodes, then the thread is joined.
- **One subtlety:** the flag is cleared *before* the thread starts, not inside the search. If the search cleared it, a `stop` that arrived before the thread got going would be lost, and `go infinite` would never end.

### Where v3.0 differs from v2, and why
- **Repetitions before the root:** a position that occurred *before* the root must repeat twice to count as a draw (real threefold). Inside the search, one repetition is enough. v2 counted any single repetition. v3 is more correct, and it is the usual rule.
- **Insufficient material:** K v K and K+minor v K are scored as draws.
- **Different trees:** the move generator produces moves in a different order and the Zobrist keys are different. Moves with equal ordering scores are tried in a different order, and TT collisions differ. So the same depth gives slightly different trees and node counts. The *evaluation* is identical (golden test).

## How to check it
```bash
tools\engine3 test
```
```bash
tools\engine3 bench
```
```bash
tools\engine3 "position startpos" "go depth 16"
```
```bash
tools\engine3 eval
```

**Test coverage:**
- **Network:** all 8 golden files in `chess_engine_v2/nets` (1,600 positions) give exactly the Python/Java numbers.
- **Accumulator:** incremental updates equal a full refresh on every move of 105 random games of up to 200 plies (captures, castling, en passant, promotions).
- **Search:** mates are found and scored by distance; stalemate is handled; results at a fixed depth are deterministic; UCI `go depth` / `go infinite` + `stop` / `go wtime` work; bad `EvalFile` values keep the current network.

## Speed (start position, single thread, i5-13500H)

| | nodes/second |
|---|---|
| v2 (Java + Vector API, after warm-up) | 1.2–2.3 M |
| v3.0 (C++, AVX2) | 6.2–6.4 M |

**Note:** v3 also has no JVM start-up. lichess-bot starts a fresh engine for every game, so v2 played its first moves with an un-warmed JIT.

## Result
SPRT v3.0 vs v2-gen-8, the **same gen-8 network** on both sides, 8+0.08: see the elo-ledger row "engine-v3.0 (C++ port…)" and the summary below.

| Test | TC | Games | W-L-D | Elo | Result |
|---|---|---|---|---|---|
| v3.0 vs v2-gen-8 (same gen-8 network) | 8+0.08 | 294 | 119-34-141 | **+103 ± 27** (nElo +163 ± 40) | ✅ H1 |

**What it means:** with the network, the search and the time management all unchanged, the C++ rewrite alone is worth about **+100 Elo**. That's 3–5× the nodes per second, and no JIT warm-up. No losses on time and no crashes in 294 games. The v3.0 binary is kept as `tools/versions/engine-v3.0.exe`, the baseline for step 3.

## Next: step 3 = search upgrades, one SPRT each
In order:
1. TT buckets + stored static eval.
2. Time management (stability, node fraction).
3. SEE + capture history.
4. LMP / futility / qsearch SEE pruning.
5. Continuation history + history-adjusted LMR.
6. Correction history.
7. Singular extensions / multi-cut / IIR.
