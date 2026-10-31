# Phase 3 — Game rules, notation, UCI, and the graphical board ✅

**Tag:** `phase-3` · **Folders:** [`phase3/`](../../phase3), [`gui/`](../../gui), [`uci/`](../../uci)

## What was built
| File | What it is |
|---|---|
| `phase3/GameRules.java`, `GameResult.java` | Checkmate, stalemate, 50-move rule, threefold repetition, insufficient material |
| `phase3/San.java` | Standard notation (`Nf3`, `exd6`, `O-O`, `e8=Q+`, `Nbd2`), written and read |
| `phase3/PgnWriter.java`, `Game.java` | A game = start FEN + moves → result and PGN |
| `phase3/Searcher.java`, `SearchLimits.java`, `SearchContext.java` | The plug for every engine version, `go` parameters, and the stop flag |
| `phase3/RandomPlayer.java` | Engine "v0": random legal moves |
| `uci/UciSession.java` | Full UCI: `go` runs on a background thread, `stop`, `setoption`, `pgn` |
| `gui/GuiServer.java`, `gui/index.html` | **The board in your browser**: play against any engine version |

## How to check it
```bash
mvn -q package -DskipTests
```

1. **The graphical board.**
   ```bash
   engine gui
   ```
   Your browser opens http://localhost:8080. To use it:
   - Click a piece; dots show its legal moves. Click a target square, or drag the piece.
   - **New as White / New as Black**, **Undo**, **Flip board**, **PGN** (downloads your game).
   - **Engine** dropdown: `v2` (strongest), `v1`, `random`. **Think time**: 0.1–10 s.
   - Paste any FEN and click **Load FEN** to set up a position.

   Stop it with `Ctrl+C` in the terminal.

2. **A game's PGN from the command line** (Fool's mate):
   ```bash
   engine "position startpos moves f2f3 e7e5 g2g4 d8h4" pgn
   ```
   Ends with `1. f3 e5 2. g4 Qh4# 0-1`.

3. **Ask an engine for a move:**
   ```bash
   engine "setoption name Engine value random" "go depth 1"
   ```

4. **Optional, in a real chess GUI:** install the free [Cute Chess](https://cutechess.com/) or
   [Arena](http://www.playwitharena.de/).
   - Add an engine with command `java`, arguments `-jar <project>\target\engine.jar`, protocol UCI.
   - It then plays like any engine: analysis, engine-vs-engine matches, and so on.

5. **Tests:**
   ```bash
   mvn test -Dtest=GameRulesTest+SanTest+SearchSetupTest+UciSessionTest
   ```

## Notes
- **Why a browser board now:** the roadmap had the website in Phase 9, but you wanted to see and
  play the engine now. `gui/` is a small developer tool (one Java file and one HTML page, no
  downloads) that reuses the same `Game` class the real website will use.
- **Threefold repetition** counts only positions since the last capture or pawn move, with the same side to move.
