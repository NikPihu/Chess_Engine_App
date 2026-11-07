# chess_engine_v3 generations

Generated from `chess_engine_v3/nets/registry.json` by `chess_engine_v3/train_new/loop.py`.
A generation becomes the live v3 network (`chess_engine_v3/nets/best.nnue`) only after the
blunder gate and SPRT H1 vs the current one. **Anchor** = a fixed 200-game match against the
same opponent every time (v3 search + v2's gen-8 net), so its Elo column is directly comparable
across generations. The Lichess bot is switched to v3 only by hand.

**Live v3 network: gen-2**

| Gen | Parent | Shape | Positions | Blunders (live) | SPRT vs live | Anchor Elo | Promoted |
|---|---|---|---|---|---|---|---|
| 1 | — | (768x4 -> 768)x2 -> 8 | 27,000,242 | 54/140 (59) | 1638 games 511-419-708 W-L-D, Elo 19.53 +/- 12.16 ✅ H1 (test SPRT after gate tolerance change) | 1638 games 511-419-708 W-L-D, Elo 19.53 +/- 12.16 (same opponent as anchor) | ✅ |
| 2 | 1 | (768x4 -> 768)x2 -> 8 | 32,000,242 | 51/140 (54) | 397-311-828 W-L-D, Elo 19.47 +/- 11.82 ✅ H1 | 63-50-87 W-L-D, Elo 22.62 +/- 32.04 | ✅ |

## Events

| Time | Event | Gen |
|---|---|---|
| 2026-11-07T14:18:54 | promote (by hand: blunder gate tolerance 0->5, test SPRT H1) | 1 |
| 2026-11-08T00:51:10 | promote | 2 |
