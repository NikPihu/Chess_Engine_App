# Chess_Engine_App
Trying to make a chess engine while taking in account all the problem solving fundamentals from DSA and also developing a entire webpage like chess.com with matchmaking and anti cheat systems

## Gambyte - the website
**Gambyte** is the chess site built on these engines: live games with clocks and ratings, our engine as
the bot *GambyteEngine* (8 levels), a Lichess-compatible bot API, puzzles, lessons, game review with a
coach, arena tournaments, Premium (Razorpay) and fair-play checks.

```bash
docker compose -f deploy/docker-compose.yml up -d     # Postgres + Redis
mvn -pl gambyte-server -am install -DskipTests && mvn -pl gambyte-server spring-boot:run
npm --prefix gambyte-web install && npm --prefix gambyte-web run dev   # http://localhost:5173
```
Start here: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · deploy: [docs/RUNBOOK.md](docs/RUNBOOK.md) · API: [docs/API.md](docs/API.md)

**Plan:** see [docs/ROADMAP.md](docs/ROADMAP.md) for phases, diagrams and how to verify each step locally.

## Quick start (Windows, Java 21 + Maven)
```bash
mvn verify                    # build + run all tests
engine gui                    # chess board in your browser - play the engine (PowerShell: .\engine gui)
engine "position startpos" d  # draw the board in the terminal
engine "go movetime 2000"     # let the engine think 2 s from the start position
engine                        # interactive UCI mode, type 'help'
```

How to check every phase: see the phase notes in [docs/phases/](docs/phases/).

## Where things are
| Folder | What |
|---|---|
| `chess_engine_v1/` | **Engine v1** (alpha-beta + PeSTO, Phases 1–6): `phase1/` … `phase6/`, its `uci/` program and `gui/` board |
| `chess_engine_v2/` | **Engine v2**: v1's search + a learned **NNUE** evaluation, improved by self-play + Lichess games ([notes](docs/phases/v2-nnue.md)) |
| `chess_engine_v3/` | **Engine v3** (C++, NNUE, Lazy SMP, Syzygy): the engine Gambyte runs |
| `gambyte-server/` | **Gambyte backend** (Spring Boot, Java 21) |
| `gambyte-web/` | **Gambyte frontend** (React + TypeScript) |
| `deploy/` | Docker images, Caddy, local/production compose |
| `tools/` | **Shared by every engine:** SPRT lab (`Sprt.java`, `Ladder.java`), Lichess bot launcher (`bot.cmd`) |
| `docs/` | Roadmap, phase notes, primers, decisions, Elo ledger |

Layout rules: [docs/CODE_LAYOUT.md](docs/CODE_LAYOUT.md)
