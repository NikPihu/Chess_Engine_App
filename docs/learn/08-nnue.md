# Primer 08 — NNUE: a neural network evaluation that runs fast on a CPU

## The problem it solves
chess_engine_v1 judges a position with hand-written rules: material plus PeSTO square tables. In
Phase 6, every extra rule we tried (king safety, mobility, passed pawns…) made the engine
**weaker**, because the weights were guesses. A neural network learns its own weights from
millions of examples.

## The network (chess_engine_v2/nnue/)
```
  768 inputs ─► 256 numbers  (side to move)      ┐
  768 inputs ─► 256 numbers  (opponent)          ├─► SCReLU ─► 512 ─► 1 number = evaluation
  (the same weights for both sides)              ┘
```
- **Inputs:** one per (piece type, colour, square), i.e. 12 × 64 = 768. A position switches on at most 32 of
  them. "White knight on g1" is one input.
- **Two perspectives:** the board is fed twice, once as seen by white and once as seen by black
  (flipped so each side sees itself at the bottom). The network always answers "how good is this for the side to move?"
- **SCReLU:** clamp each hidden number to [0, 1], then square it. This non-linearity is what lets
  the network learn combinations like "a knight on f5 is great *if* it's protected by a pawn and no
  enemy bishop can take it". Hand-written tables can't express that.

## Why "Efficiently Updatable" (the U and E in NNUE)
The first layer is just a **sum of columns**: the 256-number hidden layer equals the bias plus
the weight column of each active input. A move changes only 2–4 inputs (a piece leaves one square
and lands on another, maybe with a capture or the castling rook), so instead of summing 32
columns we:
1. copy the parent position's 256 numbers (the **accumulator**),
2. subtract the columns that switched off and add the ones that switched on.

That's a few hundred additions instead of thousands. `nnue/Accumulator.java` does this, and
`search/NnueSearch.java` keeps one accumulator per search depth. The test
`NnueTest.incrementalUpdatesEqualFullRefreshThroughRandomGames` proves the shortcut always gives
the exact from-scratch answer.

## Integers, not floats (quantisation)
Training (PyTorch) uses floating point. The engine uses `short` integers, which are faster and
smaller:
- **Feature transformer:** weights stored × 255.
- **Output layer:** weights stored × 64.
- **Activation:** `clamp(x, 0, 255)²` instead of `clamp(x, 0, 1)²`.

The maths is arranged so the integer result is the float result times a known constant.
`GoldenTest` proves Java reproduces Python's numbers exactly.

## Where the knowledge comes from
See [09-self-play-learning.md](09-self-play-learning.md).

## Further reading
- Stockfish NNUE docs: https://official-stockfish.github.io/docs/nnue-pytorch-wiki/docs/nnue.html
- Yu Nasu, *Efficiently Updatable Neural-Network-based Evaluation Functions for Computer Shogi* (2018), the original NNUE.
- https://www.chessprogramming.org/NNUE
