# Engine v3, steps 5–7: bigger network, own data pipeline, Syzygy, self-improvement loop

**Folders:** [`chess_engine_v3/src/nnue`](../../chess_engine_v3/src/nnue), [`src/datagen`](../../chess_engine_v3/src/datagen), [`src/search/syzygy.*`](../../chess_engine_v3/src/search), [`train_new/`](../../chess_engine_v3/train_new) · **Previous:** [step 4, Lazy SMP](v3-04-lazy-smp.md) · **Plan:** [v3 plan](v3-plan.md)

## Step 5: the v3 network

```
(768 inputs x 4 king buckets, mirrored -> 768) x 2 perspectives -> SCReLU -> 1536 -> 8 output heads
```

| Idea | What it does | Why it helps |
|---|---|---|
| **King buckets** (4) | Separate input weights depending on where *our* king stands: castled corner / centre or long castle / second rank / walked up | "A knight on f3" means something different with the king on g1 than on e4 |
| **Horizontal mirroring** | If our king is on files e–h, the whole board is mirrored left-right for that perspective | The network learns each king-side pattern once instead of twice: twice the data per pattern |
| **Output heads** (8) | One last layer per material stage, `(pieces − 2) / 4` | The network's version of tapered evaluation: openings and endgames get their own judgement |
| **768 wide** | 3× v2's 256 | More capacity. Worth it only with enough data (that's step 6) |

**One general C++ layout reads both formats:** v2's `NNUE` files are simply the case of 1 bucket, no mirroring, 1 head, 256 wide. v3's `NNV3` files store their shape and bucket map in the header.

**King moves:** when a king crosses into another bucket or mirror half, every input index of that perspective changes, so that one side's accumulator is rebuilt from scratch. All other moves are still 2–4 column updates.

**The trainer** (`chess_engine_v3/train_new/train.py`):
- **Streams data from memory-mapped files:** v2's trainer would need about 25 GB of RAM for 50M positions. This one runs in constant memory.
- **Reuses v2's data:** it computes the bucketed inputs from v2's 72-byte records, so all 27M existing positions are usable.

**Checks:**
- `nnue_v3_matches_python_golden_files`: C++ equals Python exactly on 200 positions (FEN in, integer out).
- `nnue_v3_incremental_with_king_buckets`: the incremental accumulator equals a full refresh on every move of 84 random games, including 3,817 bucket/mirror refreshes.

## Step 6: our own data, three sources

| Source | Command | What |
|---|---|---|
| **Self-play** | `engine_v3 datagen --positions N --out f.bin --nodes 5000` | Multi-threaded. 8 random opening plies, soft/hard node limits, quiet positions only, v2's adjudication. About 125 positions/s per thread (v2: about 35) |
| **Lichess-elite human games** | `engine_v3 pgn --in month.pgn --out f.txt --every 8`, then `engine_v3 label --in f.txt --out f.bin --nodes 10000` | Human games, both players 2200+ (database.nikonoel.fr). **Only moves and results are read:** every comment, including Lichess's Stockfish `[%eval]`, is skipped (checked: 0 in the output). Then **our own search labels every position** |
| **The bot's own games** | (v2's `lichess.bin`) | 5% of the mix |

**Downloaded so far** (git-ignored, in `tools/data/v3/elite`): Sept–Nov 2025, **847,357 games, 0 unreadable, 7,622,387 positions**.

**Our own PGN reader:** a SAN parser/writer is round-trip tested on more than 10,000 moves, including disambiguation, castling and promotions. All 176 of the bot's own Lichess games parse.

## Syzygy endgame tablebases (required)

**What they are:** solved endgames. For every position with up to 5 pieces they store win / draw / loss and the distance to the next capture or pawn move. They come from retrograde analysis, not from an engine's opinion.

**Setup:**
- **Files:** `tools/data/syzygy`, 290 files, about 984 MB (git-ignored).
- **Prober:** Fathom (`chess_engine_v3/third_party/fathom`, MIT, vendored with its license).

**How v3 uses them:**
- **In search:** any position with 5 or fewer pieces, no castling rights and a fresh 50-move counter is looked up instead of searched. A tablebase win scores 28,000 − ply: below every mate score, above every evaluation.
- **At the root:** the DTZ table picks a move that keeps the result *and* converts within the 50-move rule.
- **UCI:** `setoption name SyzygyPath value <folder>` (several folders separated by `;`).

**Why it matters, measured:** before the tables, v3 searched K+R vs K for 500 ms (28 plies deep) and still did not see the mate (score +926, no mate). That is exactly the fumbling seen on Lichess.

**With the tables:** the root probe answers immediately (`score cp 28000`, `tbhits 1`). The test `syzygy_tablebases` checks five known results (K+R v K win and loss, K+N and K+B v K draws, K+Q v K lost) and **plays K+R v K out until checkmate** using the engine's own moves.

**On Lichess (when you switch):** `tools\lichess-bot\config_new.yml` sets `SyzygyPath` for the engine and also enables lichess-bot's online tablebase (`online_egtb`) as a second safety net.

## Step 7: the self-improvement loop (`tools\train_new`)

The same habits as v2's `tools\train`: one generation per run, below-normal priority, `--stop`, `--status`, `--rollback N`. It adds the gates the generation review asked for:

1. **PLAY:** C++ self-play by the live v3 network (5M positions at 5000 nodes).
2. **ELITE:** new Lichess-elite position files are labelled by our search and make up 15% of the mix.
3. **LEARN:** a rolling window of the newest 40M positions, continuing from the parent's weights.
4. **GATES:**
   - **Blunder suite, no tolerance.** The suite was re-labelled by a 3M-node v3 search. That changed 169 of v2's 230 "best moves", and 90 of the positions turned out not to be blunders at all, so 140 real ones are kept.
   - **SPRT** vs the live v3 network.
   - **Anchor match:** a fixed 200 games against the same fixed opponent every generation (v3 search + v2's gen-8 net). It gives a cumulative Elo that self-play inflation can't fake.
5. **PROMOTE:** writes only `chess_engine_v3/nets/best.nnue`. **The Lichess bot is never switched automatically.**

**Dry run:** `--sandbox` runs it end to end without touching the real registry. Tested so far:
- gate rejection;
- self-play from a live parent;
- SPRT and the anchor match;
- `--status` and `--rollback`.
