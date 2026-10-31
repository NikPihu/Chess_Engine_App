# Code layout and versioning rules

## One concept per file
Every major idea gets its own file with a header comment explaining it, so you can learn the engine
one file at a time. Lague's code often mixes several ideas in one big file; we deliberately don't.

## Versioned packages
Every engine component lives in a versioned package:

```
engine/src/main/java/io/github/nikpihu/chess/engine/
├─ board/v1/      Square, Colour, Piece, CastlingRights, Bitboards, Board, Zobrist
├─ move/v1/       Move, MoveFlag
├─ fen/v1/        FenParser, FenWriter, FenException
└─ display/v1/    BoardPrinter, BitboardPrinter
```

The rules:
1. **A finished version is frozen.** Once its phase is tagged, `v1` code only gets bug fixes, and
   each fix comes with a test that reproduces the bug.
2. **Improvements go into a new version beside the old one.** For example, Phase 2 builds `movegen/v1`
   (simple ray loops), then `movegen/v2` (magic bitboards). Both stay in the code base.
3. **Old versions keep their tests running.** CI keeps proving every version correct, and tests can
   check a new version against an old one. For example, `movegen/v2` must produce exactly the same
   moves as `movegen/v1` on thousands of positions.
4. **Only components that evolve get new versions.** If `board/v1` never needs changing, it stays
   `v1` forever. Nothing is copied just to bump a number.
5. **Adapters are not versioned.** `engine-uci` (and later the server) just *selects* which versions
   to use.

Comparing versions is then just a diff between two folders, e.g. `search/v1` vs `search/v2`.

## Tests mirror the code
`engine/src/test/java/.../board/v1/BoardTest.java` tests `board/v1/Board.java`, and so on.
`testutil/` holds shared helpers like `RandomBoards`.
