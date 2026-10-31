# Primer 05 — Why SPRT, and how to read a result

## The problem
You change the engine. Is it stronger? Playing 20 games and counting wins is nearly useless:
- **Noise beats signal:** a +20 Elo improvement means scoring about 53% instead of 50%. With 20 games the random noise is about ±15%.
- **You'd need thousands of games:** to see a 3% difference you need thousands of games, and you don't know in advance how many.

## SPRT (Sequential Probability Ratio Test)
We state two hypotheses:
- **H0:** the change is worth at most `elo0` (we use 0), i.e. not an improvement.
- **H1:** the change is worth at least `elo1` (we use 10).

After every game pair, SPRT updates the **LLR** (log-likelihood ratio): how much more likely the results are under H1 than under H0.
- **LLR ≥ +2.94:** H1 accepted. Keep the change.
- **LLR ≤ −2.94:** H0 accepted. Reject the change.
- **In between:** keep playing.

The ±2.94 bounds come from `alpha = beta = 0.05`: at most a 5% chance of accepting a useless change, and 5% of rejecting a good one.
- **Big wins stop fast:** v1 vs random was decided after 148 games.
- **Tiny ones take long:** changes close to the bounds can need thousands of games.

## Reading a fastchess result
```
Elo: 64.2 +/- 25.1, nElo: 95.3 +/- 36.9
Games: 400, Wins: 160, Losses: 87, Draws: 153
LLR: 2.96 (-2.94, 2.94) [0.00, 10.00]
SPRT ([0.00, 10.00]) completed - H1 was accepted
```
- **Elo ± error:** estimated strength difference with a 95% interval.
- **nElo ("normalized Elo"):** Elo adjusted for the draw rate. It's what the test actually uses.
- **Pentanomial `Ptnml(0-2)`:** results counted per *pair* of games, one with each colour, on the same opening. This removes the luck of the opening.

## Our settings
| Setting | Value | Why |
|---|---|---|
| Openings | 8moves_v3, each played with both colours | Balanced and varied, without unfair positions |
| Time control | 3+0.03 for the Phase 6 ladder, 8+0.08 by default | Faster = more games per hour. Some features matter more at long time controls |
| Bounds | [0, 10] | A hobby engine still gains big chunks; Stockfish uses tighter bounds like [0, 2] |
| Max games | 3000 in the ladder | Caps hopeless cases; an undecided result counts as "not proven" |

## Further reading
- https://www.chessprogramming.org/Sequential_Probability_Ratio_Test
- https://github.com/Disservin/fastchess
