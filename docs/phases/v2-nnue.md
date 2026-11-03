# Engine v2 — NNUE + self-play learning

**Folder:** [`chess_engine_v2/`](../../chess_engine_v2) · **Primers:** [08 NNUE](../learn/08-nnue.md), [09 self-play learning](../learn/09-self-play-learning.md) · **Decision:** [ADR-0006](../adr/0006-nnue-self-play.md)

## What was built
| Folder | Files | What |
|---|---|---|
| `nnue/` | `Features`, `Network`, `Accumulator`, `NetFile` | The network: 768 inputs → 256×2 → 1, integer inference, incremental updates, `.nnue` files |
| `search/` | `NnueSearch` | v1's SPRT-proven search with the NNUE evaluation |
| `datagen/` | `SelfPlay`, `Record`, `ScoreCapture`, `Adjudicator`, `PgnPositions` | Self-play training data, and re-scoring of real Lichess games with blunder mining |
| `engine/` | `V2Main` | Adds the `nnue` engine and the `EvalFile` option to v1's UCI program and GUI |
| `train/` | `train.py`, `model.py`, `data.py`, `export.py`, `lichess_games.py`, `loop.py` | PyTorch training, export, the Lichess download, and the generation loop |
| `nets/` | `gen-N.nnue/.pt/.golden.txt`, `best.nnue`, `best.txt` | Every generation's network; `best.nnue` is bundled in the jar |

`chess_engine_v2.jar` contains **every engine**: `nnue` (default once a network is promoted), `v2`, `v1` and `random`.

## How to check it
Build everything:
```bash
mvn verify
```

**1. The engine thinks with the network:**
```bash
set ENGINE_JAR=chess_engine_v2\target\chess_engine_v2.jar
```
```bash
engine "setoption name Engine value nnue" "go movetime 2000"
```
Choose a specific generation with `"setoption name EvalFile value chess_engine_v2/nets/gen-1.nnue"`.

**2. v1 vs v2 wins and losses** (a plain match, no statistics; W/L/D at the end):
```bash
java tools/Sprt.java nnue v2 --games 200 --tc 5+0.05
```
`nnue` is chess_engine_v2; `v2` is chess_engine_v1's best search. The proper SPRT results of
every generation are in [`docs/results/elo-ledger.md`](../results/elo-ledger.md) and
[`docs/results/v2-generations.md`](../results/v2-generations.md).

**3. Watch a game live:**
```bash
engine gui
```
In the **Watch: engine vs engine** panel, pick White `nnue` and Black `v2`, then click ▶ Start.
Use the think-time dropdown to speed it up or slow it down.

**4. Replay test games:** in the board's **Replay a PGN** panel, choose any `tools/results/*.pgn`
(fastchess saves every game of every match there). Pick a game from the list, then step with
◀ ▶ or the arrow keys, or click **Auto ▶▶**.

**5. Run the learning loop yourself** (takes hours; your PC is busy meanwhile):
```bash
python chess_engine_v2/train/loop.py --generations 3
```

**6. Individual steps:**
- Download new Lichess games:
  ```bash
  python chess_engine_v2/train/lichess_games.py
  ```
- Self-play data:
  ```bash
  java -cp chess_engine_v2/target/chess_engine_v2.jar datagen.SelfPlay --positions 100000 --out tools/data/test.bin
  ```
- Train a network:
  ```bash
  python chess_engine_v2/train/train.py --data tools/data/test.bin --out tools/data/testnet --epochs 3
  ```

**7. Tests** (incremental == full refresh, Java == Python, tactics with any network):
```bash
mvn test -pl chess_engine_v2 -am
```

## Putting v2 on Lichess (your decision)
Do this only once a generation has been promoted and beats v1 (see the generations table):
1. `mvn -q package -DskipTests` (bundles `nets/best.nnue` into the jar).
2. Stop the bot with `Ctrl+C`.
3. Point the bot at the v2 jar:
   ```bash
   set ENGINE_JAR=chess_engine_v2\target\chess_engine_v2.jar
   ```
4. Restart it:
   ```bash
   tools\bot
   ```
