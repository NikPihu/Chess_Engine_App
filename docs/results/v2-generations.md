# chess_engine_v2 generations

Generated from `chess_engine_v2/nets/registry.json` by `train/loop.py`. A generation goes live
(becomes `best.nnue`, used by the Lichess bot from its next game) only after SPRT H1 vs the
current best.

**Live now: gen-2**

| Gen | Parent | Training data | Positions | Blunder suite | Test | Result | Promoted |
|---|---|---|---|---|---|---|---|
| 1 | v1 data | gen-0.bin, lichess.bin | 3,000,018 | — | v1 best (v2 search) | 15-140-19 W-L-D, Elo -314, H0 (invalid: tested with the 22x slower NNUE before the SIMD fix) | ❌ |
| 2 | v1 data | gen-1.bin, gen-0.bin, lichess.bin | 6,000,054 | — | v1 best (v2 search) | 34-138-32 W-L-D, Elo -195, H0 (invalid: slow NNUE before the SIMD fix) | ✅ |
| 2 | v1 data | gen-1.bin, gen-0.bin, lichess.bin | 6,000,054 | — | v1 best (v2 search) | 204-109-125 W-L-D, Elo +76, H1 (re-test after the SIMD fix) | ✅ |

## Events (what was live when)

| Time | Event | Gen | Lichess ratings |
|---|---|---|---|
| 2026-11-04T09:38:00 | promote | 2 | {} |
| 2026-11-04T09:59:39 | lichess-deploy | 2 | {'bullet': 2257, 'blitz': 2263, 'rapid': 3000} |
