# Elo ledger

Every engine change is tested with SPRT (fastchess, 8moves_v3 openings, both colours)
before it is accepted. Written automatically by `java tools/Sprt.java`.

| Date | Change | New | Base | TC | Games | W-L-D | Elo | nElo | LLR | Bounds | Result | Log |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 20261031 | Phase 5 lab check: v1 vs random | `v1` | `random` | 8+0.08 | 148 | 148-0-0 | inf +/- nan | inf +/- nan | 2.95 | [0, 10] | ✅ H1 (better) | 20261031-214913 |
| 20261031 | + killer moves + history heuristic | `v2:Features=killers,history` | `v2:Features=none` | 3+0.03 | 422 | 208-106-108 | 85.67 +/- 30.69 | 96.63 +/- 33.15 | 2.95 | [0, 10] | ✅ H1 (better) | 20261031-215527 |
| 20261031 | + principal variation search | `v2:Features=killers,history,pvs` | `v2:Features=killers,history` | 3+0.03 | 436 | 210-114-112 | 77.77 +/- 28.49 | 92.27 +/- 32.61 | 2.97 | [0, 10] | ✅ H1 (better) | 20261031-220105 |
| 20261031 | + null-move pruning | `v2:Features=killers,history,pvs,nmp` | `v2:Features=killers,history,pvs` | 3+0.03 | 650 | 299-202-149 | 52.24 +/- 23.27 | 60.97 +/- 26.71 | 2.95 | [0, 10] | ✅ H1 (better) | 20261031-220640 |
| 20261031 | + late move reductions | `v2:Features=killers,history,pvs,nmp,lmr` | `v2:Features=killers,history,pvs,nmp` | 3+0.03 | 426 | 208-110-108 | 81.38 +/- 28.96 | 96.39 +/- 32.99 | 2.97 | [0, 10] | ✅ H1 (better) | 20261031-221451 |
| 20261031 | + check extension | `v2:Features=killers,history,pvs,nmp,lmr,checkext` | `v2:Features=killers,history,pvs,nmp,lmr` | 3+0.03 | 1430 | 387-451-592 | -15.56 +/- 14.05 | -19.98 +/- 18.01 | -2.95 | [0, 10] | ❌ H0 (not better) | 20261031-221958 |
| 20261031 | + aspiration windows | `v2:Features=killers,history,pvs,nmp,lmr,aspiration` | `v2:Features=killers,history,pvs,nmp,lmr` | 3+0.03 | 716 | 263-177-276 | 41.93 +/- 19.21 | 56.16 +/- 25.45 | 2.95 | [0, 10] | ✅ H1 (better) | 20261031-223708 |
| 20261031 | + reverse futility pruning | `v2:Features=killers,history,pvs,nmp,lmr,aspiration,rfp` | `v2:Features=killers,history,pvs,nmp,lmr,aspiration` | 3+0.03 | 536 | 218-130-188 | 57.56 +/- 22.84 | 75.60 +/- 29.41 | 2.97 | [0, 10] | ✅ H1 (better) | 20261031-224548 |
| 20261031 | + eval: passed pawns | `v2:Features=killers,history,pvs,nmp,lmr,aspiration,rfp,passed` | `v2:Features=killers,history,pvs,nmp,lmr,aspiration,rfp` | 3+0.03 | 1300 | 408-474-418 | -17.65 +/- 14.73 | -22.69 +/- 18.89 | -2.97 | [0, 10] | ❌ H0 (not better) | 20261031-225209 |
| 20261031 | + eval: isolated + doubled pawns | `v2:Features=killers,history,pvs,nmp,lmr,aspiration,rfp,pawns` | `v2:Features=killers,history,pvs,nmp,lmr,aspiration,rfp` | 3+0.03 | 1100 | 342-415-343 | -23.09 +/- 17.24 | -27.61 +/- 20.53 | -2.96 | [0, 10] | ❌ H0 (not better) | 20261031-230803 |
| 20261031 | + eval: king safety | `v2:Features=killers,history,pvs,nmp,lmr,aspiration,rfp,kingsafety` | `v2:Features=killers,history,pvs,nmp,lmr,aspiration,rfp` | 3+0.03 | 584 | 157-237-190 | -47.90 +/- 23.75 | -57.65 +/- 28.18 | -2.96 | [0, 10] | ❌ H0 (not better) | 20261031-232153 |
| 20261031 | + eval: mobility | `v2:Features=killers,history,pvs,nmp,lmr,aspiration,rfp,mobility` | `v2:Features=killers,history,pvs,nmp,lmr,aspiration,rfp` | 3+0.03 | 3000 | 997-1000-1003 | -0.35 +/- 10.08 | -0.43 +/- 12.43 | -1.35 | [0, 10] | ⏸ inconclusive | 20261031-232852 |
| 20261101 | + eval: bishop pair | `v2:Features=killers,history,pvs,nmp,lmr,aspiration,rfp,bishoppair` | `v2:Features=killers,history,pvs,nmp,lmr,aspiration,rfp` | 3+0.03 | 3000 | 925-948-1127 | -2.66 +/- 9.77 | -3.39 +/- 12.43 | -2.09 | [0, 10] | ⏸ inconclusive | 20261101-000501 |
| 20261101 | + eval: mop-up (drive king to edge) | `v2:Features=killers,history,pvs,nmp,lmr,aspiration,rfp,mopup` | `v2:Features=killers,history,pvs,nmp,lmr,aspiration,rfp` | 3+0.03 | 3000 | 882-863-1255 | 2.20 +/- 8.90 | 3.07 +/- 12.43 | -0.48 | [0, 10] | ⏸ inconclusive | 20261101-004204 |
| 20261101 | engine-v2.0 vs engine-v1.0 | `v2:Features=killers,history,pvs,nmp,lmr,aspiration,rfp` | `v1` | 3+0.03 | 182 | 137-11-34 | 296.15 +/- 58.60 | 407.09 +/- 50.48 | 2.96 | [0, 10] | ✅ H1 (better) | 20261101-011744 |
| 20261101 | Lichess BOT nikpihu, first day | `v2` | lichess bots | 3-5 min blitz | 18 | 6-10-2 | blitz 2289 (provisional) | - | - | - | live | lichess.org/@/nikpihu |
| 20261103 | v2 gen-1 vs chess_engine_v1 best (v2 search) | `nnue:EvalFile=C:/Users/nikks/OneDrive/Desktop/SEM_WISE_PROJECTS/V_sem/PROJECTS/chess_engine/chess_engine_v2/nets/gen-1.nnue` | `v2` | 5+0.05 | 174 | 15-140-19 | -314.19 +/- 75.93 | -364.91 +/- 51.62 | -2.98 | [0, 10] | ❌ H0 (not better) | 20261103-233640 |
| 20261104 | v2 gen-2 vs chess_engine_v1 best (v2 search) | `nnue:EvalFile=C:/Users/nikks/OneDrive/Desktop/SEM_WISE_PROJECTS/V_sem/PROJECTS/chess_engine/chess_engine_v2/nets/gen-2.nnue` | `v2` | 5+0.05 | 204 | 34-138-32 | -195.42 +/- 48.71 | -236.98 +/- 47.68 | -2.97 | [0, 10] | ❌ H0 (not better) | 20261104-005554 |
