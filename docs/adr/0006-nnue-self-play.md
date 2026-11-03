# ADR-0006 — chess_engine_v2: NNUE evaluation learned by self-play

- **Status:** accepted
- **Date:** 2026-11-03

## Context
Hand-tuned evaluation terms failed SPRT in Phase 6. You want an engine that gets stronger over
time by learning from its own games, with no Stockfish involved.

## Decision
- **Search:** keep chess_engine_v1's proven search unchanged.
- **Evaluation:** replace it with a (768→256)×2 → 1 NNUE network. Integer inference in Java with incremental accumulators.
- **Training:** PyTorch on CPU (already installed), on self-play data labelled with search scores + game results. Lichess games are mixed in (≤15%).
- **Promotion:** generation by generation, only through SPRT.
- **Packaging:** v2 is its own module that depends on v1. Its jar contains all engines, so comparisons are one jar and one option.

## Consequences
- **Easier:** every generation's network is a 0.4 MB file, so it's reproducible and comparable.
  The search and every test from v1 are reused as is.
- **Harder:**
  - Training needs Python + PyTorch.
  - The data and training format is a contract kept in sync by hand between `Record.java`, `data.py`, `export.py` and `NetFile.java`. `GoldenTest` guards it.
  - The NNUE eval is slower than PeSTO, so nodes/second drops. Accuracy has to pay for that, and SPRT checks it does.
- **Not done:** policy network / MCTS (AlphaZero style). It's not practical on a laptop CPU.

## Alternatives considered
- **Stockfish-labelled data:** a faster start, but you chose pure self-play.
- **Java trainer:** no extra dependency, but much more code and slower than PyTorch's EmbeddingBag.
- **Texel-tuning the hand-written terms:** a smaller gain, and no learning loop.
