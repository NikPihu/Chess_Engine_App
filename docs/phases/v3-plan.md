# Plan: chess_engine_v3 (C++, stronger search, bigger net, self-improving loop)

## Context
- **What v2 is:** v1's Java search with an NNUE bolted on:
  - 7 proven search features;
  - 1 thread and a basic time manager;
  - a (768→256)×2 network trained on about 6M self-play positions per generation.
- **Why a new version:** the gen-2…gen-8 review showed the network loop is saturating. It gained +313 Elo in self-play, but Lichess moved only +31 in blitz and +98 in bullet, and the blunder suite went backwards. More generations of the same recipe won't move Lichess much.
- **What v3 changes:** the three things v2 never touched: search depth/quality, network size and data volume, and node speed. It keeps a self-improvement loop like v2's, with gates that catch inflated gains.
- **Timeline:**
  - gen-8 keeps playing Lichess for its ~150–200 games, and v2 stays live until then.
  - v3 is built and starts self-training in parallel.
  - The bot switches to v3 only once v3 beats v2-gen-8 in SPRT.

**Hardware facts (checked):**
- i5-13500H, 12 cores / 16 threads, 16 GB RAM.
- Intel Iris Xe, **no NVIDIA GPU**, so training stays on CPU PyTorch.
- The only C++ compiler is GCC 6.3 from 32-bit MinGW. It is too old, so a new toolchain is needed (step P0).

## How the code fits together (C++ engine, everything else stays where it is)

- **Same repo, sibling folder:** v3 is a new `chess_engine_v3/` folder in this repo, next to `chess_engine_v1/` and `chess_engine_v2/`. Same git history, same `main`, same `tools/` and `docs/`.
- **Everything inside the engine process goes to C++, top to bottom:** board, move generation, search, NNUE eval, UCI and self-play datagen. The result is one `.exe`.
- **We won't split one engine across Java and C++** (e.g. Java search calling C++ eval through JNI). Search makes millions of eval and move-generation calls per second. Every Java↔C++ crossing costs time and blocks the compiler's inlining, so a mixed engine would end up *slower* than either pure one, and harder to debug.
- **The split we keep is at the process boundary, where engines already talk UCI (plain text over stdin/stdout):**
  - **C++:** the v3 engine `.exe`.
  - **Python:** stays for training, the self-improvement loop, data tools and plots.
  - **Java stays for the app side:** the v1 GUI and match tools, and the planned Spring Boot backend. These talk UCI to *whichever* engine binary they are given, so the backend can use the C++ engine without knowing it is C++.
  - **v1/v2 Java engines stay in the repo:** they are the reference oracle (perft, golden files) and the SPRT baselines.
  - **Shared tools:** fastchess, lichess-bot, `loop.py`, `tools\*.cmd`. They just get pointed at `chess_engine_v3/build/engine_v3.exe` instead of a jar.
- **In short:** C++ is the engine, Java is the app, Python is the trainer. All three in one repo.

## Naming: `_new` for the new pieces, `_old` for what they replace
- **While gen-8 is live (now until cutover)**, nothing old is renamed or moved. The live bot depends on these exact paths:
  - `tools\bot.cmd`;
  - the v2 jar;
  - `chess_engine_v2/nets/best.nnue`, referenced by an absolute path in the lichess-bot `config.yml`;
  - `tools\train`.

  Renaming any of them now would break the running bot.
- **New pieces that replace an existing one get `_new`:**
  - `tools\train_new.cmd`, `tools\bot_new.cmd`, `tools\tune_new.cmd`;
  - `chess_engine_v3/train_new/` (trainer + loop);
  - `tools/lichess-bot/config_new.yml`.
- **The engine folder itself is `chess_engine_v3/`;** the version number already marks it as new.
- **At cutover (P8), once gen-8's 150–200 games are done and v3 has passed SPRT:**
  1. Rename the replaced pieces to `_old`: `bot_old.cmd`, `train_old.cmd`, `chess_engine_v2/train_old/`, `config_old.yml`.
  2. Make the `_new` ones the defaults (`tools\bot`, `tools\train`). The `_new` names stay as aliases so nothing you've typed stops working.
  3. **Bundle the old pieces into `old/`** (`old/chess_engine_v1`, `old/chess_engine_v2`, old tool shortcuts), **only if nothing breaks**. Checks:
     - grep for every path reference and fix them (root `pom.xml` modules, v2's import of v1, `tools\*.cmd`, the trainer's paths, docs links);
     - `mvn verify` passes;
     - v1 perft runs;
     - the v2 jar still plays an SPRT baseline game.
     - If any of these fails, the old folders stay where they are and only the `_old` labels are applied.

## Decisions (settled)
- **Language:** v3 engine in **C++** (CMake, GCC/clang with AVX2). v1/v2 serve as the oracle.
- **Training data:**
  - **Primary:** own self-play: more volume (50–100M positions), deeper labels.
  - **10–20% of the mix:** **Lichess-elite human positions**, with `[%eval]` comments stripped and **re-labelled by our own search**.
  - **Syzygy endgame tablebases (required):** the engine probes 5-piece Syzygy files during search, and lichess-bot uses Lichess's online tablebase as well, to stop fumbled won endgames.
  - **No Stockfish/Leela data.**

---

## Where the Elo comes from

The figures are from public SPRT results of hobby engines. Gains don't add up exactly, and Lichess compresses them.

| Area | Technique | Typical gain |
|---|---|---|
| Speed | C++ with AVX2, no JVM warm-up | +50–120 |
| Search | Capture history + SEE pruning (qsearch and main), LMP, futility | +40–80 |
| Search | Continuation history (1 and 2 plies) + history-adjusted LMR | +20–40 |
| Search | Correction history (pawn + non-pawn keyed) | +15–45 (larger at longer time controls) |
| Search | Singular extensions + multi-cut, IIR | +30–60 |
| Search | TT: 2–4 entry buckets, store static eval, age-aware replacement | +10–30 |
| Time management | Soft/hard limits scaled by best-move stability, score drop and node fraction | +15–30 |
| Threads | Lazy SMP, 4 threads on Lichess | +100 (about +60 per doubling) |
| Network | Wider (512 → 1024), horizontally mirrored king buckets (4), 8 material output buckets | +50–150, **only with enough data** (target 50–100M positions) |
| Data | 10× volume, deeper labels, elite-human diversity | Enables the network row above |
| Self-tuning | SPSA on search constants (LMR, margins, …), run automatically | +10–30 |

**Realistic target:** v3 about +300–500 Elo over v2 in engine-vs-engine. On Lichess, roughly 2300 → 2450–2600 blitz.

---

## Build phases (each phase: tests green + SPRT before the next)

**P0 – Toolchain and skeleton**
- Install MSYS2 (UCRT64 GCC 14) or LLVM clang via winget. This is your install; I'll give the exact commands.
- Create `chess_engine_v3/` with CMake: Release `-O3 -march=native` (AVX2/BMI2), a tests target, and a `bench` command (fixed node count, which acts as a speed + determinism fingerprint).
- Add `tools/engine3.cmd`. Make `ENGINE_JAR`-style selection in the tools accept an `.exe` (fastchess and lichess-bot already take any UCI binary).

**P1 – Board and move generation**
- Bitboards, magic sliders, 16-bit moves, make/unmake, Zobrist.
- **Perft must match v1** on the standard suite (start position, Kiwipete, positions 3–6).
- **Learning doc:** `docs/phases/v3-01-board-cpp.md`.

**P2 – v3.0 = faithful port of v2**
- UCI, TT, iterative deepening, the same 7 features, and the same time manager.
- NNUE inference that loads **v2's gen-8 `.nnue` as-is** and matches `gen-8.golden.txt` exactly.
- **Gate:** SPRT v3.0 vs v2-gen-8 (Java). Should be H1 from speed alone; this measures what C++ buys.

**P3 – Search upgrades, one SPRT each, in this order:**
1. TT buckets and storing the static eval.
2. Time management (stability, node fraction).
3. SEE + capture history.
4. LMP / futility / qsearch SEE pruning.
5. Continuation history + history-adjusted LMR.
6. Correction history.
7. Singular extensions / multi-cut / IIR.

- **The `Features` bitmask pattern from v1 carries over,** so each feature can be toggled for its A/B test.
- **Every result goes in `docs/results/elo-ledger.md`.**

**P4 – Lazy SMP**
- Add the `Threads` and `Hash` UCI options and a shared atomic TT.
- **Test:** 4 threads vs 1 thread at equal time.
- **Bot config:** 4 threads + 256 MB hash.

**P5 – New network and trainer**
- **Network:** `(768 × 4 mirrored king buckets → 768)×2 → 8 output buckets`, SCReLU, int16.
  - Width and buckets are settings, so we can grow to 1024 when the data allows.
  - The C++ engine uses "finny tables" (cached per-bucket accumulators) so king moves stay cheap.
- **Trainer rewrite** (`chess_engine_v3/train/`, still PyTorch on CPU):
  - **Streaming batch loader:** v2 builds the whole tensor in memory (about 512 B per position, so 50M positions ≈ 25 GB, which won't fit in 16 GB).
  - Bucketed features, output-bucket head, and an export that matches C++ (golden test as in v2).
- **First v3 network:** trained from **all existing data** (v2's self-play sets + Lichess). Then it grows with new data.

**P6 – Data pipeline**
- **C++ self-play datagen:** soft 5000 / hard 50k nodes, 8 random plies, quiet-position filter, the same record format plus a new compact file.
- **Rolling data store:** keep the last N sets, up to about 60M positions, instead of only the two latest.
- **`elite_positions.py`:** reads Lichess-elite PGNs, **drops all comments/evals**, samples positions, and labels them with our engine at about 10k nodes. Runs downloads are asked about first.
- **Syzygy (required):**
  - download the 5-piece Syzygy set (about 1 GB, after asking you);
  - C++ probing with the Fathom library (MIT): WDL inside the search, DTZ at the root;
  - enable lichess-bot's `online_egtb` (Lichess tablebase) as a second safety net;
  - test on the won endgames from the bot's Lichess games.
- **Meanwhile (starting now):** v2's `SelfPlay` can keep producing gen-8 positions in the background at below-normal priority while the bot plays. The data carries straight over to v3.

**P7 – Self-improvement loop v3** (`tools\train3`, same feel as `tools\train`)
- **One generation:**
  1. datagen with the current best;
  2. train (continue from the parent, rolling data);
  3. gates;
  4. promote atomically to `best.nnue`;
  5. registry and docs.
- **Gates (fixing what the review found):**
  1. SPRT vs the current best.
  2. **Anchor check:** 200 games vs a fixed anchor (v2-gen-8, later v3 milestones), with a fixed count rather than SPRT. This tracks real cumulative Elo and catches inflation.
  3. **Blunder suite:** re-labelled with a deeper search, **no tolerance**.
- **`tools\tune`:** SPSA over the search constants (LMR, RFP/futility margins, aspiration delta, SE margin), runnable overnight. Results land as new defaults only after an SPRT confirms them.
- **Stop rule:** printed by `--status`. Two generations in a row under +15 Elo means scale the network or data instead of running more generations.

**P8 – Go live**
- Once gen-8 has its ~150–200 games and v3 beats v2-gen-8 in SPRT, `tools\bot` switches to the v3 `.exe` with 4 threads.
- Lichess per-generation tracking continues (`lichess_games.py`, made engine-agnostic).

---

## Files

- **New:**
  - `chess_engine_v3/` (CMake project: `src/board`, `src/movegen`, `src/search`, `src/nnue`, `src/uci`, `src/datagen`, `tests/`)
  - `chess_engine_v3/train/` (trainer, streaming data, export)
  - `tools/train3.cmd`, `tools/tune.cmd`
  - `docs/phases/v3-*.md`
- **Reused as-is:** fastchess setup, `tools/lichess-bot`, the `docs/results/*` generators, `tools/plot_generations.py`.
- **Reused with generalisation:** `chess_engine_v2/train/loop.py` (engine path + architecture as parameters) and `chess_engine_v2/train/lichess_games.py`.
- **Oracle:** v1 perft, v2 golden files, the v2 jar as the SPRT baseline.

## Verification

- **P1:** perft equals v1 at depth 5–6 on 6 positions; the `bench` node count is stable run to run.
- **P2:** the gen-8 golden file matches exactly; SPRT v3.0 vs v2-gen-8 is H1.
- **P3/P4:** each feature has its own SPRT entry in the elo-ledger. SMP: 4 threads vs 1 thread is H1.
- **P5:** the C++ eval matches the Python golden file; the new network beats the old network at equal search.
- **P7:**
  - a dry-run generation on a small data set (e.g. 50k positions) goes through all gates;
  - a rollback works;
  - the anchor check appears in the registry.
- **P8:** the bot log shows the v3 engine, 4 threads and NNUE info; games appear on lichess.org/@/nikpihu.

**Open for your review:**
- the toolchain install (MSYS2 UCRT64 GCC);
- whether to start the background v2 self-play now.
