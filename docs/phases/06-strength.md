# Phase 6 — Strength sprints → `engine-v2.0` ✅

**Tags:** `phase-6`, `engine-v2.0` · **Folder:** [`phase6/`](../../chess_engine_v1/phase6) · **Results:** [Elo ledger](../results/elo-ledger.md)

## What was built
| File | What it is |
|---|---|
| `Search2.java` | Search v2, built on v1: PVS, null move, LMR, check extension, aspiration windows, reverse futility pruning |
| `Killers.java`, `History.java`, `MoveOrdering2.java` | Better move ordering |
| `Evaluation2.java` | Passed pawns, pawn structure, king safety, mobility, bishop pair, mop-up |
| `Features.java` | An on/off switch for every feature (UCI option `Features`) |
| `tools/Ladder.java` | Adds the features one at a time and keeps only those that SPRT proves |

## Ladder results (3+0.03, SPRT [0, 10])
| Feature | Elo | Kept? |
|---|---|---|
| Killers + history | +86 | ✅ |
| PVS | +78 | ✅ |
| Null-move pruning | +52 | ✅ |
| Late move reductions | +81 | ✅ |
| Check extension | — | ❌ |
| Aspiration windows | +42 | ✅ |
| Reverse futility pruning | +58 | ✅ |
| Passed pawns | −18 | ❌ |
| Isolated/doubled pawns | −23 | ❌ |
| King safety | −48 | ❌ |
| Mobility | ±0 | ❌ |
| Bishop pair | — | ❌ |
| Mop-up | +2 ± 9 | ❌ (undecided after 3,000 games) |
| **Final: v2 vs v1** | **+296 ± 59** (137-11-34) | ✅ |

v2's default features: `killers,history,pvs,nmp,lmr,aspiration,rfp`.

**Why all the evaluation terms failed:**
- **Their weights were hand-guessed.** They partly double-count what PeSTO's tables already know.
- **They slow every evaluation down,** which hurts at a fast time control.
- **King safety's penalty was far too harsh.**
- **Next step:** Texel tuning, which fits the weights to game results instead of guessing them, then re-test each term.

## How to check it
```bash
mvn -q package -DskipTests
```

Compare the search depth of v1 and v2 (in 2 seconds, v1 reached depth 8 and v2 depth 13):
```bash
engine "setoption name Engine value v1" "position startpos" "go movetime 2000"
```
```bash
engine "position startpos" "go movetime 2000"
```

Try any feature combination:
```bash
engine "setoption name Features value all,-kingsafety" "go depth 10"
```

Re-run a test yourself:
```bash
java tools/Sprt.java v2 v1 --tc 3+0.03
```

Or run the whole ladder again (takes hours):
```bash
java tools/Ladder.java
```
