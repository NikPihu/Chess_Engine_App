# chess_engine_v2 generations

Each generation learns from self-play of the previous best (plus Lichess games) and is
promoted only if SPRT proves it stronger. Written by `chess_engine_v2/train/loop.py`.

| Gen | Data | Positions | Trained from | Test | Result | Promoted |
|---|---|---|---|---|---|---|
| 1 | gen-0.bin + lichess | 3,000,018 | random init | vs v1 best | 15-140-19 W-L-D, Elo -314.19 +/- 75.93 | ❌ no |
| 2 | gen-1.bin, gen-0.bin + lichess | 6,000,054 | random init | vs v1 best | 34-138-32 W-L-D, Elo -195.42 +/- 48.71 | ❌ no |
