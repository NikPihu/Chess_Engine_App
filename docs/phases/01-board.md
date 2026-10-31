# Phase 1 — Board, FEN, Zobrist ✅

**Tag:** `phase-1` · **Primer:** [learn/01-board-fen-move-encoding.md](../learn/01-board-fen-move-encoding.md)

## What was built
| File | What it is |
|---|---|
| `board/v1/Square.java` | Squares as ints 0–63 (a1 = 0, h8 = 63), names like "e4" |
| `board/v1/Colour.java` | WHITE = 0, BLACK = 1 |
| `board/v1/Piece.java` | 4-bit pieces: `colour << 3 \| type` (white 1–6, black 9–14) |
| `board/v1/CastlingRights.java` | 4-bit mask KQkq |
| `board/v1/Bitboards.java` | `long` bitboard helpers: file/rank masks, count, lowest square |
| `board/v1/Zobrist.java` | Fixed-seed random keys, plus a hash-from-scratch function used to check the incremental one |
| `board/v1/Board.java` | Bitboards + mailbox kept in sync, game state, incremental Zobrist key |
| `move/v1/Move.java`, `MoveFlag.java` | 16-bit `FFFF TTTTTT SSSSSS` move encoding (Lague V2 flags) |
| `fen/v1/FenParser.java`, `FenWriter.java` | FEN in and out, with clear error messages |
| `display/v1/BoardPrinter.java`, `BitboardPrinter.java` | ASCII board and bitboard grids |

**Suggested reading order:** Square → Colour → Piece → Bitboards → CastlingRights → Zobrist → Board → FenParser → FenWriter → Move.

## See it yourself
First build the jar (do this after every code change):
```bash
mvn -q package -DskipTests
```

**1. Draw the start position**
```bash
java -jar engine-uci/target/engine-uci.jar "position startpos" d
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
java -jar engine-uci/target/engine-uci.jar "position fen r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1" d
```

**3. Look inside a bitboard.** `bb n` shows the black knights. Try also `bb P`, `bb white`, `bb all`.
```bash
java -jar engine-uci/target/engine-uci.jar "position fen r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1" "bb n"
```
```
 8  . . . . . . . .
 7  . . . . . . . .
 6  . 1 . . . 1 . .
 ...
hex:   0x0000220000000000
count: 2
```

**4. Interactive mode.** Start the jar with no arguments and type commands; `help` lists them all.
```bash
java -jar engine-uci/target/engine-uci.jar
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
java -jar engine-uci/target/engine-uci.jar "position fen 8/8/8/8/8/8/8/8 w - - 0 1" d
```

**6. Run the tests**
```bash
mvn test
```
There are 89 tests, including:
- 1,000 random positions round-tripped through FEN;
- the incremental Zobrist key checked against a from-scratch hash after 10,000 random edits;
- every invalid-FEN case giving a clear message.

## Learnings / notes
- `h8` is bit 63, the sign bit of a `long`. Always use `>>>` (unsigned shift), never `>>`
  (`BitboardsTest.highestBitIsHandled`).
- The en-passant square is hashed whenever it's set, even if no capture is possible. This is simple and
  correct for our engine. Polyglot opening books hash it only when a capture is possible; we'll handle
  that if we ever read Polyglot books.
- Board legality checks that need attack detection ("the side not to move is in check") arrive in Phase 2.
