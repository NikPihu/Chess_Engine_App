# gambyte-web

The frontend of **Gambyte**: React 19 + TypeScript + Vite + Tailwind CSS 4.

## Run

```bash
npm install
```
```bash
npm run dev
```

- **Dev server:** opens at http://localhost:5173. It forwards `/api` and `/ws` to the Spring Boot server on :8080 (see `vite.config.ts`), so start the server too (see `../gambyte-server/README.md`).
- **Other scripts:**

| Script | What it does |
|---|---|
| `npm run typecheck` | TypeScript, with no output files |
| `npm test` | Vitest unit and component tests |
| `npm run build` | Production build into `dist/` (served by Caddy in production) |
| `npm run e2e` | Playwright smoke test against a running stack |

## Layout

```
src/
  App.tsx              routes (pages are lazy-loaded)
  lib/                 api.ts (HTTP + CSRF), ws.ts (STOMP), chess.ts, sound.ts, types.ts
  components/          Layout, Logo, Spinner
  pages/               Home (lobby), Leaderboard, NotFound
  features/
    auth/              login, register, password reset, useMe()
    game/              Board, Clock, MoveList, PlayerBar, GamePage, useLiveGame
    lobby/             challenges
    puzzle/            puzzles and Puzzle Rush
    learn/             lessons
    analysis/          analysis board, Game Review, eval bar, AI coach chat
    profile/           profile, settings, useSettings()
    social/            friends, notifications, reports, TV
    tournament/        arenas
    premium/           pricing and Razorpay checkout
    bots/              Bot API guide
    admin/             moderation
```

## Conventions

- **Data:**
  - server data uses TanStack Query (`useQuery` / `useMutation`) with keys like `['game', id]`;
  - live data uses `subscribe(destination, handler, onReconnect)` from `lib/ws.ts`;
  - on reconnect, refetch the snapshot.
- **Legality:** the server decides it. chess.js is only used for instant UI feedback.
- **Types:** `lib/types.ts` mirrors the server's DTO records; update both together.
- **Styles:** Tailwind utilities, plus a few shared ones (`card`, `btn-primary`, `input`...) in `styles.css`.
