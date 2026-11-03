# Phase 7 — Lichess bot ✅

**Files:** [`tools/bot.cmd`](../../tools/bot.cmd), your clone in `tools/lichess-bot/` (git-ignored, holds your token)

## What was set up
- **lichess-bot** ([github.com/lichess-bot-devs/lichess-bot](https://github.com/lichess-bot-devs/lichess-bot)) runs our engine through `java -jar`.
- **Your `tools/lichess-bot/config.yml`** (backup in `config.yml.before-chessengineapp`):

  | Setting | Value | Why |
  |---|---|---|
  | `engine.name` | `ChessEngineApp.jar` | Our engine |
  | `interpreter` | `java` | It's a Java jar |
  | `interpreter_options` | `-Xmx512m -jar` | Memory cap and jar launch |
  | `ponder` | `false` | The engine doesn't think on the opponent's time yet |
  | `uci_options` | `Hash: 128`, `Move Overhead: 100` | Only options our engine has |

- **Move Overhead** (new UCI option): keeps 100 ms of every move in reserve for network lag, so the bot doesn't lose on time.
- **`tools\bot.cmd`** copies the latest `chess_engine_v1/target/chess_engine_v1.jar` into `tools/lichess-bot/engines/` and starts lichess-bot.

## Connection check (done, 2026-10-31)
```
Engine configuration OK        ← lichess-bot started our engine over UCI
Welcome nikpihu!               ← your token works
nikpihu is not a bot account.  ← expected: not upgraded yet
```

## Live results
- **2026-11-01:** account **nikpihu** upgraded to BOT and online: https://lichess.org/@/nikpihu
- **First day:** 18 blitz games, **6 wins, 2 draws, 10 losses**. Blitz rating **2289**, still
  provisional (a "?" rating until about 20+ games; it started at 3000? and is settling).
- **No bullet games**, because matchmaking only sends 3–5 minute blitz challenges
  (`matchmaking.challenge_initial_time`). Bullet happens only when someone challenges it.

## Going live: your steps (done)
> ⚠️ **Upgrading to BOT is permanent.** A BOT account can never play as a human again, and Lichess only
> allows the upgrade on an account that has **never played a game**. If `nikpihu` is your personal
> account, **don't upgrade it**. Instead:
> 1. Create a new Lichess account, e.g. `ChessEngineApp-bot`.
> 2. Log in as it, go to https://lichess.org/account/oauth/token, and create a token with the
>    **"Play games with the bot API"** (`bot:play`) scope.
> 3. Put that token in `tools/lichess-bot/config.yml` (the `token:` line). Never commit it or share it.

Then, in a terminal in the project folder:

1. Build the engine:
   ```bash
   mvn -q package -DskipTests
   ```
2. Check the setup. It should say "Welcome <bot account>":
   ```bash
   tools\bot
   ```
3. **One time only, irreversible:** upgrade the new account to BOT:
   ```bash
   tools\bot -u
   ```
4. Go online. Leave this window open while the bot plays:
   ```bash
   tools\bot
   ```

## How to check it
- **Challenge it:** from your normal account, open `https://lichess.org/@/<bot-name>` and click
  **Challenge to a game**. Use rapid or blitz, e.g. 5+3, standard chess.
- **Watch it:** the terminal shows each move and the engine's evaluation, and the game appears live on Lichess.
- **Rating:** after about 20 rated games against other bots, note its Lichess rating in the Elo ledger.

## Notes
- **One game at a time** (`challenge.concurrency: 1`): our engine uses one CPU thread.
- **Challenges it accepts:** standard chess only, bullet to classical, rated and casual.
