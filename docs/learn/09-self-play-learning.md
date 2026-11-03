# Primer 09 — How the engine teaches itself (self-play learning)

## The loop (chess_engine_v2/train/loop.py)
```
            ┌───────────────────────────────────────────────────────────┐
            ▼                                                           │
  1. PLAY     the best engine plays thousands of games against itself   │
              (+ the Lichess bot's real games are downloaded)           │
  2. LABEL    every quiet position gets: the engine's search score       │
              + the final game result                                    │
  3. LEARN    PyTorch trains a new network on those labels               │
  4. PROVE    SPRT match: new network vs current best (thousands of games)│
  5. PROMOTE  only if SPRT says "stronger"  ─────────────────────────────┘
```

### Why it improves
- **Search beats eval.** A score from a 5,000-node search is more accurate than the static
  evaluation at the root of that search. Training the network to predict search scores
  *distils* search knowledge into the eval. The next engine then searches with a better
  eval, so its scores are better again, which gives better labels next time.
- **The game result is the reinforcement signal.** The target is
  `0.75 × sigmoid(score / 400) + 0.25 × result`. A position the engine thought was +1 but which
  went on to be lost pulls the network's opinion down. This is what the engine actually
  "experiences".
- **SPRT is the safety gate.** A generation that got worse is never promoted, so the best network can only get stronger.

### Gen 1 starts from chess_engine_v1
No network exists at first, so generation 1's games are played by v1's best search (PeSTO eval).
The network learns PeSTO's knowledge **plus** what the 5,000-node searches and game results reveal
beyond it. From generation 2 on, the network learns from its own games.

## Learning from Lichess
Lichess is too slow to be the main source: about 100 games/day, versus 3 million positions per
generation from self-play. But its games are valuable for a different reason. In self-play, both
sides share the same blind spots, so nobody punishes them. Real opponents do.
- `train/lichess_games.py` downloads the bot's new games.
- `datagen/PgnPositions.java` re-scores every position with a **10× deeper** search (more
  accurate labels than the game itself had) and the real result.
- **Blunder mining:** where the bot's own move lost more than 150 cp, that position gets 4×
  weight and goes into `chess_engine_v2/tests/lichess-blunders.epd`.
- Lichess data is capped at 15% of training, so a few games can't dominate.

The deployed bot never changes itself mid-play. New knowledge goes live only after SPRT proves
it, and only when you restart the bot with the new network.

## How this relates to AlphaZero / Leela
| | Our loop | AlphaZero / Leela |
|---|---|---|
| What learns | evaluation (value) only | value **and** policy (which moves to consider) |
| Search | alpha-beta (our proven search) | Monte Carlo Tree Search (PUCT) |
| Data per cycle | ~3M positions, CPU | millions of games, many GPUs |
| Starts from | v1's knowledge | random |

Our loop is the practical CPU version of the same idea: improve from your own games, keep only
what proves stronger. A policy network + MCTS is a possible later experiment, but it needs far
more compute than a laptop.
