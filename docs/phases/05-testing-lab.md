# Phase 5 — Testing lab (fastchess + SPRT + Elo ledger) ✅

**Tag:** `phase-5` · **Files:** [`tools/Sprt.java`](../../tools/Sprt.java), [`tools/sprt.cmd`](../../tools/sprt.cmd) · **Primer:** [learn/05-sprt-explained.md](../learn/05-sprt-explained.md)

## What was built
- **`tools/Sprt.java`:** runs straight from source with `java tools/Sprt.java ...`, no build step. It:
  1. copies `chess_engine_v1/target/chess_engine_v1.jar` to `tools/versions/` so a long test is never affected by a rebuild;
  2. plays both engines with fastchess, using `8moves_v3` openings with each opening played with both colours;
  3. stops as soon as SPRT decides;
  4. appends the result to [`docs/results/elo-ledger.md`](../results/elo-ledger.md).
- **Engine specs:**
  - Any name from `uci/Engines.java` (`random`, `v1`, `v2`).
  - Optionally with UCI options, e.g. `"v2:Features=all,-nmp"`.

## One-time setup (already done on your machine)
Both files are git-ignored:
- `tools/bin/fastchess.exe`: fastchess 1.8.2, from https://github.com/Disservin/fastchess/releases (file `fastchess-windows-x86-64.zip`).
- `tools/books/8moves_v3.pgn`: from https://github.com/official-stockfish/books (file `8moves_v3.pgn.zip`).

## How to check it
```bash
mvn -q package -DskipTests
```
```bash
java tools/Sprt.java v1 random
```
Or the shortcut:
```bash
tools\sprt v1 random
```
fastchess prints a running score. At the end you'll see something like:
```
Games: 148, Wins: 148, Losses: 0, Draws: 0
SPRT ([0.00, 10.00]) completed - H1 was accepted
Ledger updated: H1 (better)
```
Then open `docs/results/elo-ledger.md` to see the new row.

**Useful flags:**
- `--tc 3+0.03`: faster games.
- `--concurrency 8`: number of games at once. The default uses 3/4 of your CPU threads.
- `--elo0 0 --elo1 10`: the SPRT bounds.
- `--maxgames 3000`: stop there if still undecided.
- `--label "my change"`: the name in the ledger.

All games are saved in `tools/results/<timestamp>.pgn`. You can replay them in `engine gui`, or open them in any chess program.
