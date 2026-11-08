# Lichess: v2 gen-8 vs v3 gen-1 (nikpihu)

All of the bot's games since v2 gen-8 went live, downloaded on 2026-11-08 (public API, 136 games).

| Group | Period (UTC) | What played |
|---|---|---|
| **v2 gen-8** | 2026-11-06 02:53 → 2026-11-07 08:52 | Java engine, 1 thread, gen-8 net |
| **v3 gen-1** | 2026-11-07 08:52 → 19:21 | C++ engine, 4 threads, Syzygy, gen-1 net |
| v3 later | 2026-11-07 19:21 → 2026-11-08 00:33 | v3; gen-1 or gen-2 (the exact time the bot was restarted on gen-2 is not known) |

Most v3 gen-1 games were played **while gen-2 was training and running its SPRT** on the same laptop, so v3 had less CPU than it normally would.

## Results

| | Games | W-L-D | Score | Avg opponent | Performance | Rating change |
|---|---|---|---|---|---|---|
| v2 gen-8 blitz | 71 | 30-24-17 | 54% | 2290 | ~2319 | 2293 → 2304 |
| v2 gen-8 bullet | 16 | 9-3-4 | 69% | 2303 | ~2440 | 2355 → 2385 |
| **v3 gen-1 blitz** | 27 | 22-2-3 | **87%** | ~2310 | **~2626** | 2304 → 2396 |
| **v3 gen-1 bullet** | 6 | 5-0-1 | **92%** | ~2280 | ~2700 | 2385 → 2411 |
| v3 all (gen-1 + later), blitz | 40 | 30-4-6 | 82% | 2344 | ~2614 | 2304 → 2437 |
| v3 all, bullet | 9 | 7-0-2 | 89% | 2297 | ~2658 | 2385 → 2419 |

- **Blitz rating:** +133 in 40 v3 games, compared with +11 in 71 gen-8 games.
- **Caveat on the numbers:** performance figures from 30 games are rough (±100 or more), and the 87–92% scores will come down as the rating climbs and the opponents get stronger. The direction is still unmistakable: a sample this size wouldn't produce a jump this large by chance.

## How the play changed

Every move of every game was re-checked by engine_v3 with the gen-2 net (300k nodes per position, Syzygy on). All figures are from the bot's point of view.

| Measure | v2 gen-8 | v3 gen-1 |
|---|---|---|
| Average centipawn loss per move | 27 | **17** |
| Blunders per game (move loses ≥ 2 pawns) | 0.48 | **0.09** (5× fewer) |
| Inaccuracies per game (loses 0.8–2 pawns) | 3.05 | **1.33** |
| Average eval after move 10 | +0.11 | **+0.69** |
| Average eval after move 20 | +1.22 | **+2.01** |
| Games where the bot was ever −2 or worse | 37 of 87 (43%) | **3 of 33 (9%)** |
| Games that reached +3 but weren't won | 7 of 45 | **1 of 28** |
| Median move where trouble first began (eval ≤ −2) | move 21 | move 43 |

**1. It almost stopped losing games outright.**
- gen-8 got into a losing position (≤ −2) in 43% of its games, usually in the middlegame (median move 21). 25 of its 27 losses were mates.
- v3 gen-1 was in trouble in only 3 of 33 games, and much later (median move 43).
- This is mostly search: deeper lines, 4 threads, and the pruning upgrades see tactics coming that gen-8's single-thread Java search walked into.

**2. It gets an edge earlier and keeps it.**
- After move 10, v3 is already about +0.7 on average, compared with +0.1 for gen-8.
- Wins typically come from a slow squeeze rather than an opponent's blunder: the advantage grows steadily (+1 → +3 → +6 → mate), with no big jumps in the eval.

**3. It converts.**
- gen-8 let 7 of its 45 clearly won games (+3 or better) slip to draws or losses.
- v3 gen-1 let only 1 of 28 slip.
- Syzygy and the online tablebase take care of the endgames that v2 used to fumble.

## Sample games

- **gen-8 throws away a win: [2AdimV0H](https://lichess.org/2AdimV0H)** (black vs Chess3Bot 2261, Pseudo-Benko).
  - After 18.Bf3+ the bot was **+3.7**.
  - 21...Re8 dropped it to +0.5. Then the bot drifted into a worse rook endgame, and 33...Ra4 (−5.6 → −11.7) lost outright.
  - This is the typical v2 pattern: an advantage reached in the middlegame, then a lost thread that can't be repaired.
- **gen-8 drifts, then collapses: [Ib3QwNvh](https://lichess.org/Ib3QwNvh)** (black vs OpeningsBot 2422).
  - The bot was **+3.6 at move 15** from a kingside pawn storm (…h5–h4).
  - 19...Be4, 20...Ndf6 and 22...Qb6 each gave a bit away (+3.4 → +0.4). The king was left exposed, and 39...Kh7 (−2.8 → −8.8) walked into a mating attack.
  - gen-8's search was too shallow to see that the h-file it had opened would later be used against its own king.
- **v3 squeezes: [90bavFLI](https://lichess.org/90bavFLI)** (white vs RavenEngine 2210, Slav Exchange).
  - A quiet, symmetrical opening, level until move 25.
  - v3 then built up on the c-file (Rac1, Na4–c5, doubled rooks) and gave up the exchange with 41.Rxc6 for a strong passed pawn and active pieces.
  - The eval rose steadily: +1 at move 25, +3.5 at 30, +6 at 40, +11.6 at 50, then mate.
  - No single swing was bigger than about 1.5 pawns. It's a long, positional plan, the kind a deeper search plus a better evaluation makes possible.
- **v3 converts a small endgame edge: [ttKhHDGd](https://lichess.org/ttKhHDGd)** (white vs AconcaguaBot 2288).
  - The queens came off at move 11 (…Qxd1+). v3 held about +3 through a long minor-piece endgame from move 20 to move 40 without letting it slip.
  - It then broke through on the kingside, and the opponent resigned.
  - gen-8 used to draw this kind of endgame.
- **v3 vs a stronger bot: [ZbLVbcHO](https://lichess.org/ZbLVbcHO)** (white vs KirinChessBot 2466, Semi-Tarrasch).
  - v3 built a classical centre and slowly increased the pressure (+1.5 → +4.6 → +7.7 → +11).
  - It finished with mate, against a bot rated about 130 points above it.
- **v3's one squandered win: [Cdn04uWa](https://lichess.org/Cdn04uWa)** (black vs Ndr1sn 2235, Najdorf-style Sicilian).
  - The bot reached **+5.4** after 29.Rfe1.
  - 28...f6 had already weakened the e6 square, and from there the rook endgame slipped away to a loss.
  - It shows v3 still has weak spots in sharp, open-king middlegames.

## Summary

The change from gen-8 to v3 gen-1 is about **+300 Elo in performance on Lichess** so far. That's far bigger than the +19.5 Elo the gen-1 network alone measured in engine-vs-engine testing, because most of the gain comes from the new engine:
- **The C++ search:** +271 Elo vs the v2 search port in SPRT.
- **4 threads:** +174.
- **Syzygy tablebases** for endgames.

Every SPRT so far showed the same thing, and Lichess now confirms it: **v2 lost games through middlegame blunders, and v3 mostly doesn't make them.**
