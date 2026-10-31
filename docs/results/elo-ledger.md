# Elo ledger

Every engine change is tested with SPRT (fastchess, 8moves_v3 openings, both colours)
before it is accepted. Written automatically by `java tools/Sprt.java`.

| Date | Change | New | Base | TC | Games | W-L-D | Elo | nElo | LLR | Bounds | Result | Log |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 20261031 | Phase 5 lab check: v1 vs random | `v1` | `random` | 8+0.08 | 148 | 148-0-0 | inf +/- nan | inf +/- nan | 2.95 | [0, 10] | ✅ H1 (better) | 20261031-214913 |
