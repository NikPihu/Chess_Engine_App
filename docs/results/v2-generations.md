# chess_engine_v2 generations

Generated from `chess_engine_v2/nets/registry.json` by `train/loop.py`. A generation goes live
(becomes `best.nnue`, used by the Lichess bot from its next game) only after SPRT H1 vs the
current best.

**Live now: gen-6**

| Gen | Parent | Training data | Positions | Blunder suite | Test | Result | Promoted |
|---|---|---|---|---|---|---|---|
| 1 | v1 data | gen-0.bin, lichess.bin | 3,000,018 | — | v1 best (v2 search) | 15-140-19 W-L-D, Elo -314, H0 (invalid: tested with the 22x slower NNUE before the SIMD fix) | ❌ |
| 2 | v1 data | gen-1.bin, gen-0.bin, lichess.bin | 6,000,054 | — | v1 best (v2 search) | 34-138-32 W-L-D, Elo -195, H0 (invalid: slow NNUE before the SIMD fix) | ✅ |
| 2 | v1 data | gen-1.bin, gen-0.bin, lichess.bin | 6,000,054 | — | v1 best (v2 search) | 204-109-125 W-L-D, Elo +76, H1 (re-test after the SIMD fix) | ✅ |
| 3 | 2 | selfplay-gen2-for-gen3.bin, gen-1.bin, lichess.bin | 6,000,054 | 61/115 (gen-2: 63, neutral suite) | gen-2 blunder suite | failed gate  | ✅ |
| 3 | 2 | selfplay-gen2-for-gen3.bin, gen-1.bin, lichess.bin | 6,000,054 | 61/115 (gen-2: 63, neutral suite) | gen-2 | 678-571-613 W-L-D, Elo 19.99 +/- 12.91, ✅ H1 (better) (after gate fix) | ✅ |
| 4 | 3 | selfplay-gen3-for-gen4.bin, selfplay-gen2-for-gen3.bin, lichess.bin | 6,000,042 | 81/148 (gen-3: 77) | gen-3 | 153-58-89 W-L-D, Elo 113.94 +/- 33.10, ✅ H1 (better)  | ✅ |
| 5 | 4 | selfplay-gen4-for-gen5.bin, selfplay-gen3-for-gen4.bin, lichess.bin | 6,000,024 | 82/151 (gen-4: 81) | gen-4 | 206-112-180 W-L-D, Elo 66.38 +/- 25.75, ✅ H1 (better)  | ✅ |
| 6 | 5 | selfplay-gen5-for-gen6.bin, selfplay-gen4-for-gen5.bin, lichess.bin | 6,000,050 | 81/165 (gen-5: 89) | gen-5 | 367-274-309 W-L-D, Elo 34.12 +/- 17.55, ✅ H1 (better)  | ✅ |

## Events (what was live when)

| Time | Event | Gen | Lichess ratings |
|---|---|---|---|
| 2026-11-04T09:38:00 | promote | 2 | {} |
| 2026-11-04T09:59:39 | lichess-deploy | 2 | {'bullet': 2257, 'blitz': 2263, 'rapid': 3000} |
| 2026-11-04T12:49:26 | promote | 3 | {'bullet': 2325, 'blitz': 2252, 'rapid': 3000} |
| 2026-11-04T20:03:59 | promote | 4 | {'bullet': 2359, 'blitz': 2255, 'rapid': 3000} |
| 2026-11-04T22:17:59 | promote | 5 | {'bullet': 2359, 'blitz': 2272, 'rapid': 3000} |
| 2026-11-05T00:51:40 | promote | 6 | {'bullet': 2371, 'blitz': 2278, 'rapid': 3000} |
