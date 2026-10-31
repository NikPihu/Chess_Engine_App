# Phase 4 — Search and evaluation v1 → `engine-v1.0` ✅

**Tags:** `phase-4`, `engine-v1.0` · **Folder:** [`phase4/`](../../phase4)

## What was built
| File | What it is |
|---|---|
| `Search.java` | Negamax alpha-beta, quiescence search, iterative deepening, repetition and 50-move draws, mate scores |
| `TranspositionTable.java` | Hash table of searched positions: depth-preferred, aged, mate scores fixed by ply (Lague's bugs fixed) |
| `MoveOrdering.java` | Hash move → MVV-LVA captures → promotions, with lazy selection sort (the PQ-vs-heap discussion) |
| `PestoTables.java`, `Evaluation.java` | PeSTO piece-square tables, tapered between middlegame and endgame |
| `TimeManager.java` | How long to think for `movetime` and real clocks |

## How to check it
```bash
mvn -q package -DskipTests
```

1. **Watch it think.** Each `info` line is one finished depth: score in centipawns, nodes searched, best line (`pv`).
   ```bash
   engine "setoption name Engine value v1" "position startpos" "go movetime 2000"
   ```
   Expect depth 8 in about 1 second, at roughly 5–6 million nodes/second.

2. **Finds a mate:**
   ```bash
   engine "setoption name Engine value v1" "position fen 6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1" "go depth 5"
   ```
   Expect `score mate 1` and `bestmove d1d8`.

3. **Play it:** run `engine gui`, choose `v1` in the Engine dropdown, and play.

4. **Tests** (mate in 1 and 2 with a brute-force proof, free queen, poisoned pawn, time limits, stop, never loses to random):
   ```bash
   mvn test -Dtest=SearchTest+EvaluationTest
   ```

## Notes
- **v1 vs random** scored **148–0** in the Phase 5 lab (first row of the [Elo ledger](../results/elo-ledger.md)).
- **The evaluation is symmetric:** colour-flipped positions give identical scores (tested).
