# gambyte-server

The backend of **Gambyte**: Spring Boot 3.5 on Java 21. One deployable, split into feature
packages; see [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) for the big picture.

## Run

```bash
docker compose -f ../deploy/docker-compose.yml up -d
```
```bash
mvn -pl gambyte-server -am install -DskipTests
```
```bash
mvn -pl gambyte-server spring-boot:run
```

- **Local URLs:** the server runs at http://localhost:8080; API docs are at http://localhost:8080/swagger-ui.html.
- **Configuration:** everything is in `src/main/resources/application.yml`, and every value can be overridden by an environment variable (`gambyte.engine.path` → `ENGINE_PATH`).

## Test

```bash
mvn -pl gambyte-server test
```

| Kind | Classes |
|---|---|
| Unit | `Glicko2Test`, `ChessClockTest`, `LiveGameTest`, `ReviewMathTest`, `FairPlayStatsTest`, `RazorpaySignatureTest` |
| Architecture | `ArchitectureTest` (feature boundaries) |
| Integration | `GambyteIntegrationTest`: real Postgres + Redis in Docker via Testcontainers; skipped without Docker |

## Layout

```
src/main/java/com/gambyte/
  auth/        login, register, session principal
  user/        accounts, profiles, ratings, leaderboards, presence
  game/        live games, clocks, moves, endings, archive, PGN, WebSocket
  lobby/       quick pairing (Redis sorted sets) and challenges
  rating/      Glicko-2
  engine/      engine process pool and the 8 computer levels
  bot/         API tokens and the Lichess-compatible Bot API
  puzzle/      puzzles, importer, engine-based puzzle miner
  learn/       lessons (JSON in resources/lessons)
  analysis/    eval, Game Review queue, free coach, optional AI coach
  social/      follows, notifications, reports, TV
  tournament/  arena tournaments
  payment/     Premium with Razorpay
  fairplay/    post-game engine comparison and flags
  admin/       moderation API
  common/ config/ notification/   errors, rate limits, security, WebSocket, email
src/main/resources/
  db/migration/   Flyway SQL (never edit an applied one; add V<n+1>)
  lessons/        one JSON file per lesson
  puzzles/        seed puzzles loaded into an empty database
```

## Adding a feature (the usual recipe)

1. **Schema:** add a migration `V<n>__<name>.sql`.
2. **Code:** create `com.gambyte.<feature>` with a service (logic) and a controller (HTTP); use DTO records, not entities, in responses.
3. **Other features:** need something from another feature? Call its service. Need to react to something? Listen to its Spring event.
4. **Security:** public GET endpoints go in `SecurityConfig`; everything else requires login by default.
5. **Tests:** add a unit test; add to `GambyteIntegrationTest` if the feature touches the database.
