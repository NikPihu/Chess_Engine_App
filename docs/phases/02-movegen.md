# Phase 2 — Move generation, make/unmake, perft ✅

**Tag:** `phase-2` · **Folder:** [`phase2/`](../../chess_engine_v1/phase2)

## What was built
| File | What it is |
|---|---|
| `Attacks.java` | Precomputed knight, king and pawn attack tables |
| `RayAttacks.java` | Rook/bishop attacks by walking rays: slow but obviously correct |
| `Magics.java` | Magic bitboards: rook/bishop attacks in O(1). We **find our own magic numbers** at start-up |
| `Lines.java` | `BETWEEN[a][b]` and `LINE[a][b]` tables, used for checks and pins |
| `MoveList.java` | Reusable `int[]` move list (no memory allocation while searching) |
| `Position.java` | Board + **make/unmake** with an undo stack, key history, null move |
| `MoveGenerator.java` | **Legal** moves using check masks, pins, en-passant discovered checks and castling rules |
| `Perft.java`, `Bench.java` | Counts move sequences (correctness proof) and measures speed |
| `UciMoveParser.java` | Turns "e2e4" text into our move format |

**Suggested reading order:** Attacks → RayAttacks → Lines → Magics → Position → MoveGenerator → Perft.

## How to check it
```bash
mvn -q package -DskipTests
```

1. **The famous number.** Perft 5 from the start must print `Nodes searched: 4865609`:
   ```bash
   engine "position startpos" "go perft 5"
   ```

2. **Divide.** The per-move counts let you compare against any other engine, e.g. Stockfish's `go perft`:
   ```bash
   engine "position fen r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1" "go perft 3"
   ```
   Expect `Nodes searched: 97862`.

3. **Play moves and look at the result.** Here you castle, and en passant becomes possible:
   ```bash
   engine "position startpos moves e2e4 a7a6 e4e5 d7d5" d moves
   ```
   The `moves` list contains `e5d6`, the en-passant capture.

4. **Speed:**
   ```bash
   engine bench
   ```
   About 90 million nodes/second on your machine.

5. **Tests:** run the whole perft suite (21 positions) and the magic-vs-ray cross-check:
   ```bash
   mvn test -Dtest=PerftTest+MagicsTest+MoveGeneratorTest
   ```

## Notes
- **Every perft position matches the published counts**, including the tricky ones: en passant that would expose the king, castling through check, and under-promotions.
- **Magics are tested against the simple ray code** on 20,000 random boards.
