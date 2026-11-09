# Gambyte runbook

> **First deployment?** Follow [DEPLOY_STEP_BY_STEP.md](DEPLOY_STEP_BY_STEP.md): every click and command, in order.

How to run Gambyte on your machine, put it on the internet (AWS free tier), and do the
day-to-day operations.

## 1. Run it locally

**Prerequisites:** Java 21, Maven, Node 22+, Docker Desktop. Optionally, the built C++ engine;
without it the server uses the weaker Java engine.

```bash
docker compose -f deploy/docker-compose.yml up -d
```
```bash
mvn -pl gambyte-server -am install -DskipTests
```
```bash
mvn -pl gambyte-server spring-boot:run
```
```bash
npm --prefix gambyte-web install
```
```bash
npm --prefix gambyte-web run dev
```

Open http://localhost:5173.

- **Emails** (verification, password reset) are printed in the server log until an SMTP server is configured.
- **API docs** are at http://localhost:8080/swagger-ui.html.
- **To make yourself admin:** register, then restart the server with `GAMBYTE_ADMIN_USERNAME=<you>`.

**Engine:** build it once with `tools\engine3 build` (Windows) or `cmake` (see `chess_engine_v3/CMakeLists.txt`). The server finds it at `../chess_engine_v3/bin/engine_v3.exe`; override the path with `ENGINE_PATH`.

**Tests:**

| Command | What it runs | Needs |
|---|---|---|
| `mvn -pl gambyte-server test` | Unit, ArchUnit and Testcontainers integration tests | Docker |
| `npm --prefix gambyte-web test` | Vitest | — |
| `npm --prefix gambyte-web run e2e` | Playwright | The running stack, plus `npx playwright install chromium` once |

## 2. Deploy on AWS (free tier, about $0)

This setup costs about $0. The pieces:

| Piece | Host |
|---|---|
| Server, engine, Caddy | One **EC2 t3.micro** (x86, 1 GB RAM) |
| Postgres | Container on the same box, or the free **Neon** tier |
| Redis | Container on the same box, or the free **Upstash** tier |

> Use **x86 (t3/t2)**, not Graviton (t4g). The engine needs the x86 BMI2 instruction (PEXT).

1. **Launch an instance.**
   - **EC2 → Launch instance:** Ubuntu 24.04 LTS, `t3.micro`, 16-30 GB gp3 disk, a key pair.
   - **Security group:** allow TCP 22 (from your IP), 80 and 443 (from anywhere).
2. **Install Docker and add swap.** 1 GB RAM is tight, and swap prevents out-of-memory kills:
   ```bash
   sudo apt update && sudo apt install -y docker.io docker-compose-v2 git
   ```
   ```bash
   sudo usermod -aG docker ubuntu
   ```
   ```bash
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile && echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   ```
   Log out and in again so the `docker` group applies.
3. **Get the code and configure it:**
   ```bash
   git clone https://github.com/NikPihu/Chess_Engine_App.git && cd Chess_Engine_App
   ```
   ```bash
   cp deploy/.env.example deploy/.env && nano deploy/.env
   ```
   - **First run, test by IP:** `SITE_ADDRESS=:80`, `SITE_URL=http://<public-ip>`, `COOKIE_SECURE=false`.
   - **With a domain:** point an A record at the instance's Elastic IP, then set `SITE_ADDRESS=play.example.com`, `SITE_URL=https://play.example.com`, `COOKIE_SECURE=true`. Caddy gets the certificate automatically.
   - **No domain?** DuckDNS gives a free `name.duckdns.org`.
4. **Start it.** The first build takes about 10 minutes on a t3.micro:
   ```bash
   docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
   ```
   ```bash
   docker compose -f deploy/docker-compose.prod.yml logs -f server
   ```
5. **Optional: move the databases off the box.**
   - Create a Neon project and an Upstash Redis database (both free, no card).
   - Put their URLs in `DATABASE_URL` / `REDIS_URL` and set `COMPOSE_PROFILES=` (empty).
   - This frees about 300 MB of RAM for the engine.

**Upgrading:** `git pull` then the same `up -d --build` command.
- **Database:** migrations run by themselves.
- **Live games:** they survive the restart via their Redis snapshots.

**When to pay:** a t3.micro handles a few dozen concurrent players. The first upgrade is a `t3.small`
(2 GB, about $15/month); then set `ENGINE_POOL_SIZE=2`, `ENGINE_HASH_MB=64` and `JAVA_OPTS=-Xmx900m`.

## 3. Payments (Razorpay)

1. **Get test keys:** Razorpay Dashboard → **Test mode** → Settings → API Keys gives `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET`.
2. **Create the plans:** Subscriptions → Plans: monthly ₹199 and yearly ₹1499 (the prices shown in `PremiumService.PLANS`). Put their ids in `RAZORPAY_PLAN_MONTHLY` / `RAZORPAY_PLAN_YEARLY`.
3. **Add the webhook:**
   - **URL:** `https://<your-domain>/api/payments/webhook`.
   - **Events:** `subscription.activated`, `subscription.charged`, `subscription.cancelled`, `subscription.halted`, `subscription.completed`.
   - **Secret:** put it in `RAZORPAY_WEBHOOK_SECRET`.
4. **Test:** pay with Razorpay's test card or test UPI id. Premium turns on immediately. The webhook keeps renewals in sync, and replaying a webhook changes nothing (it's idempotent).
5. **Go live:** complete KYC in Razorpay, switch to live keys and plans, restart.

## 4. Optional services

- **Email:** AWS SES (free for low volume). Verify your domain, create SMTP credentials, and set `MAIL_*`.
- **AI coach:**
  - set `ANTHROPIC_API_KEY`;
  - it is Premium-only and rate-limited;
  - the default model is `claude-opus-5-5`; `COACH_MODEL=claude-haiku-5-5` is far cheaper.
- **Syzygy tablebases:** copy the `.rtbw`/`.rtbz` files to the server and set `SYZYGY_HOST_PATH` and `ENGINE_SYZYGY_PATH=/syzygy`.

## 5. Puzzles

- **Built in:** a fresh install loads `gambyte-server/src/main/resources/puzzles/seed.csv`. These are puzzles mined by our own engine from Lichess Elite games.
- **More of your own:**
  ```bash
  java -cp gambyte-server.jar -Dloader.main=com.gambyte.puzzle.PuzzleMiner org.springframework.boot.loader.launch.PropertiesLauncher <engine> <fens.txt> <out.csv> 8 100000
  ```
  Then import the result (below), or replace `seed.csv` with it.
- **The Lichess database** (CC0, about 4 million puzzles):
  - download `lichess_db_puzzle.csv.zst` from https://database.lichess.org/#puzzles and decompress it;
  - as an admin, call `POST /api/admin/puzzles/import?path=/path/lichess_db_puzzle.csv&max=200000`.

## 6. Operations cheat sheet

| Task | How |
|---|---|
| Health | `curl https://<domain>/actuator/health` |
| Logs | `docker compose -f deploy/docker-compose.prod.yml logs -f server` (JSON in prod) |
| Database backup | `docker compose -f deploy/docker-compose.prod.yml exec postgres pg_dump -U gambyte gambyte > backup.sql` |
| Restore | `cat backup.sql \| docker compose ... exec -T postgres psql -U gambyte gambyte` |
| Make a moderator | Admin page → Users → "Make moderator" |
| Review cheating flags | Admin page → Flags (nothing is ever auto-banned) |
| Audit trail | Server log lines from logger `gambyte.audit` |
