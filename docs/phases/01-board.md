# Phase 1 — Board, FEN, Zobrist ✅

**Tag:** `phase-1` · **Folder:** [`phase1/`](../../chess_engine_v1/phase1) · **Primer:** [learn/01-board-fen-move-encoding.md](../learn/01-board-fen-move-encoding.md)

## What was built (all in `phase1/`)
| File | What it is |
|---|---|
| `Square.java` | Squares as ints 0–63 (a1 = 0, h8 = 63), names like "e4" |
| `Colour.java` | WHITE = 0, BLACK = 1 |
| `Piece.java` | 4-bit pieces: `colour << 3 \| type` (white 1–6, black 9–14) |
| `CastlingRights.java` | 4-bit mask KQkq |
| `Bitboards.java` | `long` bitboard helpers: file/rank masks, count, lowest square |
| `Zobrist.java` | Fixed-seed random keys, plus a hash-from-scratch function used to check the incremental one |
| `Board.java` | Bitboards + mailbox kept in sync, game state, incremental Zobrist key |
| `Move.java`, `MoveFlag.java` | 16-bit `FFFF TTTTTT SSSSSS` move encoding (Lague V2 flags) |
| `FenParser.java`, `FenWriter.java`, `FenException.java` | FEN in and out, with clear error messages |
| `BoardPrinter.java`, `BitboardPrinter.java` | ASCII board and bitboard grids |

Tests are in `phase1/tests/`, one file per class, plus `RandomBoards.java` (random-position
generator) and `fen-corpus.txt` (real test positions).

**Suggested reading order:** Square → Colour → Piece → Bitboards → CastlingRights → Zobrist → Board → FenParser → FenWriter → Move.

## See it yourself
Open a terminal in the project folder. Rebuild after every code change:
```bash
mvn -q package -DskipTests
```

These commands use `engine` (cmd). **In PowerShell, type `.\engine`.** The long form
`java -jar chess_engine_v1/target/chess_engine_v1.jar ...` works everywhere.

**1. Draw the start position**
```bash
engine "position startpos" d
```
```
     a   b   c   d   e   f   g   h
   +---+---+---+---+---+---+---+---+
 8 | r | n | b | q | k | b | n | r | 8
 ...
 1 | R | N | B | Q | K | B | N | R | 1
   +---+---+---+---+---+---+---+---+
     a   b   c   d   e   f   g   h

FEN:        rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1
Side:       white
Castling:   KQkq
En passant: -
Clocks:     halfmove 0, fullmove 1
Zobrist:    0x4F1557D5EE54104C
```

**2. Any position from FEN** (Kiwipete, the famous perft test position)
```bash
engine "position fen r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1" d
```

**3. Look inside a bitboard.** `bb n` shows the black knights. Try also `bb P`, `bb white`, `bb all`.
```bash
engine "position fen r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1" "bb n"
```
```
 8  . . . . . . . .
 7  . . . . . . . .
 6  . 1 . . . 1 . .
 ...
hex:   0x0000220000000000
count: 2
```

**4. Interactive mode.** Start with no arguments and type commands; `help` lists them all.
```bash
engine
```
```
help
position fen rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1
d
bb all
quit
```

**5. See an error message.** A FEN with no kings is rejected and the previous position is kept:
```bash
engine "position fen 8/8/8/8/8/8/8/8 w - - 0 1" d
```

**6. Run the tests**
```bash
mvn test
```
There are 89 tests, including:
- 1,000 random positions round-tripped through FEN;
- the incremental Zobrist key checked against a from-scratch hash after 10,000 random edits;
- every invalid-FEN case giving a clear message.

Run one test class only with `mvn test -Dtest=ZobristTest`.

## Learnings / notes
- `h8` is bit 63, the sign bit of a `long`. Always use `>>>` (unsigned shift), never `>>`
  (`BitboardsTest.highestBitIsHandled`).
- The en-passant square is hashed whenever it's set, even if no capture is possible. This is simple and
  correct for our engine. Polyglot opening books hash it only when a capture is possible; we'll handle
  that if we ever read Polyglot books.
- Board legality checks that need attack detection ("the side not to move is in check") arrive in Phase 2.
