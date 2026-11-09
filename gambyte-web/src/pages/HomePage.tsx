/**
 * The lobby: quick pairing pools, a game against Gambyte Engine, a friend challenge link, and
 * any game you are still playing.
 */
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { Bot, Loader2, Swords, UserPlus, Play } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import type { GameView } from '@/lib/types';
import { subscribe } from '@/lib/ws';
import { useMe } from '@/features/auth/useAuth';
import { ChallengeDialog } from '@/features/lobby/ChallengeDialog';

/** Time controls offered for quick pairing: [minutes, increment seconds]. */
const POOLS: [number, number][] = [
  [1, 0], [2, 1], [3, 0],
  [3, 2], [5, 0], [5, 3],
  [10, 0], [10, 5], [15, 10],
  [30, 0], [30, 20], [60, 0],
];

const LEVELS = [
  { n: 1, label: 'Beginner', rating: 600 },
  { n: 2, label: 'Novice', rating: 900 },
  { n: 3, label: 'Casual', rating: 1200 },
  { n: 4, label: 'Club', rating: 1500 },
  { n: 5, label: 'Strong club', rating: 1800 },
  { n: 6, label: 'Expert', rating: 2100 },
  { n: 7, label: 'Master', rating: 2400 },
  { n: 8, label: 'Full strength', rating: 2800 },
];

interface LobbyStats {
  online: number;
  playing: number;
  waiting: Record<string, number>;
}

export default function HomePage() {
  const { data: me } = useMe();
  const navigate = useNavigate();
  const [seeking, setSeeking] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [challengeOpen, setChallengeOpen] = useState(false);

  const stats = useQuery({
    queryKey: ['lobby-stats'],
    queryFn: () => api.get<LobbyStats>('/api/lobby/stats'),
    refetchInterval: 10_000,
  });
  const ongoing = useQuery({
    queryKey: ['ongoing'],
    queryFn: () => api.get<GameView[]>('/api/me/games/ongoing'),
    enabled: Boolean(me),
  });

  // The server tells us privately when a pairing is found.
  useEffect(() => {
    if (!me) return;
    return subscribe('/user/queue/match', (body) => {
      const { gameId } = body as { gameId: string };
      setSeeking(null);
      navigate(`/game/${gameId}`);
    });
  }, [me, navigate]);

  async function seek(min: number, inc: number) {
    if (!me) return navigate('/login?next=/');
    const key = `${min}+${inc}`;
    setError(null);
    if (seeking === key) {
      setSeeking(null);
      await api.del('/api/lobby/seek').catch(() => undefined);
      return;
    }
    try {
      setSeeking(key);
      const r = await api.post<{ gameId?: string }>('/api/lobby/seek', { initial: min * 60, increment: inc, rated: true });
      if (r.gameId) navigate(`/game/${r.gameId}`);
    } catch (e) {
      setSeeking(null);
      setError(errorMessage(e));
    }
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
      <div className="space-y-6">
        {!me && (
          <section className="card overflow-hidden bg-gradient-to-br from-brand-700/40 to-ink-850 p-8">
            <h1 className="text-3xl font-extrabold tracking-tight sm:text-4xl">Play chess. Get better. For free.</h1>
            <p className="mt-3 max-w-xl text-ink-300">
              Real-time games against people and bots, puzzles, lessons, and a coach that explains your mistakes -
              powered by our own engine, Gambyte Engine.
            </p>
            <div className="mt-6 flex flex-wrap gap-3">
              <Link to="/register" className="btn-primary px-6 py-3 text-base">Create a free account</Link>
              <Link to="/puzzles" className="btn-secondary px-6 py-3 text-base">Try a puzzle</Link>
            </div>
          </section>
        )}

        {ongoing.data && ongoing.data.length > 0 && (
          <section className="card p-4">
            <h2 className="mb-3 font-semibold">Your games in progress</h2>
            <div className="flex flex-wrap gap-2">
              {ongoing.data.map((g) => (
                <Link key={g.id} to={`/game/${g.id}`} className="btn-primary">
                  <Play size={16} /> vs {g.white.id === me?.id ? g.black.username : g.white.username}
                </Link>
              ))}
            </div>
          </section>
        )}

        <section className="card p-4">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="flex items-center gap-2 font-semibold">
              <Swords size={18} /> Quick pairing
            </h2>
            {stats.data && (
              <span className="text-xs text-ink-400">
                {stats.data.online} online · {stats.data.playing} playing
              </span>
            )}
          </div>
          <div className="grid grid-cols-3 gap-2">
            {POOLS.map(([min, inc]) => {
              const key = `${min}+${inc}`;
              const active = seeking === key;
              return (
                <button
                  key={key}
                  onClick={() => seek(min, inc)}
                  className={clsx(
                    'flex flex-col items-center rounded-xl border py-5 transition',
                    active
                      ? 'border-brand-500 bg-brand-600/20'
                      : 'border-ink-700 bg-ink-800 hover:border-ink-600 hover:bg-ink-700',
                  )}
                >
                  <span className="text-2xl font-bold">{key}</span>
                  <span className="text-xs text-ink-400">
                    {active ? (
                      <span className="flex items-center gap-1 text-brand-400">
                        <Loader2 size={12} className="animate-spin" /> searching… (click to cancel)
                      </span>
                    ) : (
                      perfName(min * 60, inc) + (stats.data?.waiting[key] ? ` · ${stats.data.waiting[key]} waiting` : '')
                    )}
                  </span>
                </button>
              );
            })}
          </div>
          {error && <p className="mt-3 text-sm text-red-300">{error}</p>}
        </section>
      </div>

      <aside className="space-y-6">
        <PlayComputer />
        <section className="card p-4">
          <h2 className="mb-2 flex items-center gap-2 font-semibold">
            <UserPlus size={18} /> Play a friend
          </h2>
          <p className="mb-3 text-sm text-ink-400">Create a link and send it to anyone.</p>
          <button className="btn-secondary w-full" onClick={() => (me ? setChallengeOpen(true) : navigate('/login'))}>
            Create challenge
          </button>
        </section>
      </aside>
      {challengeOpen && <ChallengeDialog onClose={() => setChallengeOpen(false)} />}
    </div>
  );
}

/** Start a game against Gambyte Engine: choose level, colour and time. */
function PlayComputer() {
  const { data: me } = useMe();
  const navigate = useNavigate();
  const [level, setLevel] = useState(3);
  const [color, setColor] = useState<'white' | 'random' | 'black'>('random');
  const [time, setTime] = useState<[number, number]>([10, 0]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const info = LEVELS[level - 1];

  async function start() {
    if (!me) return navigate('/login?next=/');
    setBusy(true);
    setError(null);
    try {
      const g = await api.post<GameView>('/api/games/computer', {
        level,
        color,
        initial: time[0] * 60,
        increment: time[1],
      });
      navigate(`/game/${g.id}`);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card p-4">
      <h2 className="mb-3 flex items-center gap-2 font-semibold">
        <Bot size={18} /> Play Gambyte Engine
      </h2>
      <label className="label" htmlFor="level">
        Level {level}: {info.label} <span className="normal-case text-ink-400">(~{info.rating})</span>
      </label>
      <input
        id="level"
        type="range"
        min={1}
        max={8}
        value={level}
        onChange={(e) => setLevel(Number(e.target.value))}
        className="w-full accent-brand-500"
      />
      <div className="mt-3">
        <span className="label">Time</span>
        <div className="grid grid-cols-4 gap-1">
          {([[3, 2], [10, 0], [15, 10], [0, 0]] as [number, number][]).map(([m, i]) => (
            <button
              key={`${m}+${i}`}
              onClick={() => setTime([m, i])}
              className={clsx('rounded-lg py-1.5 text-sm', time[0] === m && time[1] === i ? 'bg-brand-600 text-white' : 'bg-ink-800 hover:bg-ink-700')}
            >
              {m === 0 ? '∞' : `${m}+${i}`}
            </button>
          ))}
        </div>
      </div>
      <div className="mt-3">
        <span className="label">Your colour</span>
        <div className="grid grid-cols-3 gap-1">
          {(['white', 'random', 'black'] as const).map((c) => (
            <button
              key={c}
              onClick={() => setColor(c)}
              className={clsx('rounded-lg py-1.5 text-sm capitalize', color === c ? 'bg-brand-600 text-white' : 'bg-ink-800 hover:bg-ink-700')}
            >
              {c}
            </button>
          ))}
        </div>
      </div>
      <button className="btn-primary mt-4 w-full" onClick={start} disabled={busy}>
        {busy ? 'Starting…' : 'Play'}
      </button>
      {error && <p className="mt-2 text-sm text-red-300">{error}</p>}
    </section>
  );
}

function perfName(initialSec: number, inc: number): string {
  const est = initialSec + 40 * inc;
  if (est < 180) return 'Bullet';
  if (est < 480) return 'Blitz';
  if (est < 1500) return 'Rapid';
  return 'Classical';
}
