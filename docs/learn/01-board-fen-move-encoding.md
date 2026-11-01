# Primer 01 — Board representation, FEN and move encoding

Read this before Phase 1. It takes about 20 minutes. The links at the bottom go deeper.

## 1. Squares
We number squares **a1 = 0, b1 = 1 … h1 = 7, a2 = 8 … h8 = 63**.

```
rank = square / 8    (0 = rank 1)       square = rank * 8 + file
file = square % 8    (0 = file a)
```

Moving "up" one rank is `+8`, "right" one file is `+1`, and the diagonals are `+9` and `+7`.
Watch the board edges: `h4 + 1` wraps around to `a5`. Bitboard shifts need *file masks* to prevent this.

## 2. Bitboards
A `long` has 64 bits, one per square. Bit *n* set means "something is on square *n*".

```
whitePawns at start = 0x000000000000FF00L   (rank 2)
```

The tricks you'll use constantly:

| Need | Java |
|---|---|
| Is square s occupied? | `(bb >>> s & 1L) != 0` |
| Number of pieces | `Long.bitCount(bb)` |
| Lowest set square | `Long.numberOfTrailingZeros(bb)` |
| Remove lowest bit | `bb &= bb - 1` |
| All pawns push one | `(pawns << 8) & ~occupied` |

Loop over every piece in a bitboard:
```java
while (bb != 0) {
    int sq = Long.numberOfTrailingZeros(bb);
    // ... use sq ...
    bb &= bb - 1;
}
```

**Why both bitboards *and* a `mailbox[64]`?**
- Bitboards answer *set* questions fast, such as "every square a knight attacks that holds an enemy piece".
- The mailbox answers "what is on e4?" in O(1).

Keeping both in sync costs a few instructions per move and saves many lookups. This is the design
Lague moved to in V2.

## 3. FEN
```
rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1
   └────────────── placement ─────────────┘ │  │   │  │ └ fullmove number
                                            │  │   │  └ halfmove clock (50-move rule)
                                            │  │   └ en-passant target square (or -)
                                            │  └ castling rights (or -)
                                            └ side to move
```
- Placement runs from **rank 8 down to rank 1**, files a→h. Digits are runs of empty squares.
  Uppercase is White, lowercase is Black.
- The en-passant square is the square *behind* the pawn that just double-pushed.
  Many writers emit it even when no capture is possible; parse both forms.
- Test idea: `toFen(parse(fen)).equals(fen)` for a large set of positions.

## 4. Move encoding (16 bits)
```
 15 14 13 12 | 11 10 9 8 7 6 | 5 4 3 2 1 0
   flag      |    target     |   start
```
| Flag | Meaning |
|---|---|
| 0 | quiet / normal capture |
| 1 | en passant |
| 2 | castling |
| 3 | pawn double push |
| 4 | promote to queen |
| 5 | promote to knight |
| 6 | promote to rook |
| 7 | promote to bishop |

```java
int move   = start | (target << 6) | (flag << 12);
int start  = move & 0x3F;
int target = (move >>> 6) & 0x3F;
int flag   = move >>> 12;
boolean isPromotion = flag >= 4;
```
Captures are *not* in the flag: whether the target square holds a piece is checked on the board.
This keeps the encoding small, and 16 bits leaves room to pack a score into the upper bits of an
`int` later (useful for move ordering).

## 5. Zobrist hashing
- Pre-generate random 64-bit numbers:
  - one for every (piece, square) pair (12 × 64);
  - one for each castling-rights combination (16);
  - one per en-passant file (8);
  - one for "black to move".
- A position's hash is the XOR of the numbers for everything present.
- Because `x ^ k ^ k == x`, making a move only XORs out the old state and XORs in the new, which is O(1) per move.
- Use a **fixed seed** so hashes are reproducible across runs (needed for opening books and tests).

## Go deeper
- https://www.chessprogramming.org/Board_Representation
- https://www.chessprogramming.org/Bitboards
- https://www.chessprogramming.org/Forsyth-Edwards_Notation
- https://www.chessprogramming.org/Zobrist_Hashing
- Lague V2: `Chess-Coding-Adventure/src/Core/Board/` on branch `Chess-V2-UCI`
