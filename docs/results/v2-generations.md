# chess_engine_v2 generations

Each generation learns from self-play of the previous best (plus Lichess games) and is
promoted only if SPRT proves it stronger. Written by `chess_engine_v2/train/loop.py`.

| Gen | Data | Positions | Trained from | Test | Result | Promoted |
|---|---|---|---|---|---|---|
| 1 | gen-0.bin + lichess | 3,000,018 | random init | vs v1 best | 15-140-19 W-L-D, Elo -314.19 +/- 75.93 | ❌ no |
| 2 | gen-1.bin, gen-0.bin + lichess | 6,000,054 | random init | vs v1 best | 34-138-32 W-L-D, Elo -195.42 +/- 48.71 | ❌ no |
| 2 (re-test) | same network | — | — | vs v1 best, **after the SIMD speed fix** | 204-109-125 W-L-D, ~+76 Elo, SPRT H1 | ✅ **yes** |

> **Note (2026-11-04):** the first two tests ran with a 22× slower NNUE inference (132k vs 2.9M
> nodes/s), so the networks searched ~5 plies shallower and lost on depth. After the fix (Vector API
> SIMD + fused accumulator updates, 2.49M nodes/s) gen-2 beats chess_engine_v1's best search.
> Gen-1's result is also unrepresentative; it was not re-tested.
