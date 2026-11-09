/**
 * Keeps one game's state in sync with the server.
 *
 * 1. Fetch the full snapshot (GET /api/games/{id}).
 * 2. Subscribe to /topic/game/{id} and apply each small event (a move, the end...) locally.
 * 3. If an event's version number skips one (a message was missed) or the socket reconnects,
 *    fetch the snapshot again. Versions make this self-healing without any clever merging.
 *
 * Moves made by the user are shown immediately ("optimistic") and sent over the WebSocket; the
 * server's event then confirms them. If the server rejects a move, the snapshot is re-fetched.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '@/lib/api';
import { replay } from '@/lib/chess';
import type { GameEvent, GameView } from '@/lib/types';
import { currentLagMs, send, subscribe } from '@/lib/ws';
import { playMoveSound, sound } from '@/lib/sound';

export interface ChatMessage {
  by: string;
  text: string;
}

export function useLiveGame(id: string) {
  const [game, setGame] = useState<GameView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [chat, setChat] = useState<ChatMessage[]>([]);
  const [rematchId, setRematchId] = useState<string | null>(null);
  const gameRef = useRef<GameView | null>(null);
  gameRef.current = game;

  const refresh = useCallback(async () => {
    try {
      const g = await api.get<GameView>(`/api/games/${id}`);
      setGame(g);
      setError(null);
    } catch {
      setError('Game not found');
    }
  }, [id]);

  useEffect(() => {
    setGame(null);
    setChat([]);
    setRematchId(null);
    void refresh();
    const unsubscribe = subscribe(
      `/topic/game/${id}`,
      (body) => {
        const e = body as GameEvent;
        const g = gameRef.current;
        if (e.t === 'chat') {
          setChat((c) => [...c.slice(-99), { by: e.by ?? '?', text: e.text ?? '' }]);
          return;
        }
        if (e.t === 'rematch' && e.rematchId) {
          setRematchId(e.rematchId);
          return;
        }
        if (!g) return;
        if (e.t === 'move' && e.uci) {
          const alreadyShown = g.moves.length === e.ply && g.moves[g.moves.length - 1] === e.uci;
          if (!alreadyShown && e.ply !== g.moves.length + 1) return void refresh(); // missed something
          if (!alreadyShown) playMoveSound(e.san);
          setGame({
            ...g,
            fen: e.fen ?? g.fen,
            moves: alreadyShown ? g.moves : [...g.moves, e.uci],
            sans: alreadyShown ? [...g.sans.slice(0, -1), e.san ?? ''] : [...g.sans, e.san ?? ''],
            clock: e.clock ?? g.clock,
            turn: (e.fen ?? g.fen).split(' ')[1] === 'w' ? 'white' : 'black',
            drawOffer: undefined,
            version: e.v,
          });
        } else if (e.t === 'end') {
          sound.end();
          setGame({
            ...g,
            status: e.status ?? g.status,
            winner: e.winner,
            termination: e.termination,
            clock: e.clock ? { ...e.clock, running: undefined } : g.clock,
            white: { ...g.white, ratingDiff: e.whiteRatingDiff },
            black: { ...g.black, ratingDiff: e.blackRatingDiff },
            version: e.v,
          });
        } else if (e.t === 'draw') {
          setGame({ ...g, drawOffer: (e.by as GameView['drawOffer']) ?? undefined, version: e.v });
        } else if (e.t === 'rematch') {
          setGame({ ...g, rematchOffer: (e.by as GameView['rematchOffer']) ?? undefined });
        } else {
          void refresh(); // "state" and anything unknown: take the full snapshot
        }
      },
      () => void refresh(),
    );
    // Rejected moves come back on the private error queue.
    const unsubErrors = subscribe('/user/queue/errors', () => void refresh());
    return () => {
      unsubscribe();
      unsubErrors();
    };
  }, [id, refresh]);

  /** Plays the user's move: show it now, send it, and let the server confirm. */
  const move = useCallback(
    (uci: string): boolean => {
      const g = gameRef.current;
      if (!g || g.status !== 'STARTED') return false;
      let chess;
      try {
        chess = replay(g.startFen, g.moves);
        const m = chess.move({ from: uci.slice(0, 2), to: uci.slice(2, 4), promotion: uci[4] });
        playMoveSound(m.san);
        setGame({
          ...g,
          fen: chess.fen(),
          moves: [...g.moves, uci],
          sans: [...g.sans, m.san],
          turn: g.turn === 'white' ? 'black' : 'white',
        });
      } catch {
        return false;
      }
      const body = { uci, lag: currentLagMs() };
      if (!send(`/app/game/${id}/move`, body)) {
        api.post(`/api/games/${id}/move`, body).catch(() => void refresh());
      }
      return true;
    },
    [id, refresh],
  );

  const action = useCallback(
    async (path: string) => {
      try {
        const r = await api.post<{ gameId?: string } | GameView | undefined>(`/api/games/${id}/${path}`);
        if (r && 'gameId' in r && r.gameId) setRematchId(r.gameId);
        if (path === 'takeback') void refresh();
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed');
        setTimeout(() => setError(null), 3000);
      }
    },
    [id, refresh],
  );

  const say = useCallback(
    (text: string) => api.post(`/api/games/${id}/chat`, { text }).catch(() => undefined),
    [id],
  );

  return { game, error, chat, rematchId, move, action, say, refresh };
}
