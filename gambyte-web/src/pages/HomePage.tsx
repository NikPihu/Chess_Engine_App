/**
 * "/" - the landing page for visitors, the dashboard for players.
 *
 * Dashboard layout (card structure after the "Checkmate" reference): greeting + ratings strip,
 * then a play column (quick pairing, engine, friend) and a training column (daily puzzle,
 * lessons, live TV), with games in progress pinned on top.
 */
import { lazy, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { ArrowRight, ArrowUpRight, Bot, GraduationCap, Loader2, Play, Puzzle, Swords, Tv, UserPlus } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import type { GameView, Perf } from '@/lib/types';
import { subscribe } from '@/lib/ws';
import { useMe } from '@/features/auth/useAuth';
import { ChallengeDialog } from '@/features/lobby/ChallengeDialog';
import { Board } from '@/features/game/Board';
import { useSettings } from '@/features/profile/useSettings';
import { Spinner } from '@/components/Spinner';

const LandingPage = lazy(() => import('./LandingPage'));

/** Time controls offered for quick pairing: [minutes, increment seconds]. */
const POOLS: [number, number][] = [
  [1, 0], [2, 1], [3, 0], [3, 2],
  [5, 0], [5, 3], [10, 0], [10, 5],
  [15, 10], [30, 0], [30, 20], [60, 0],
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
  const { data: me, isLoading } = useMe();
  if (isLoading) return <Spinner />;
  return me ? <Dashboard /> : <LandingPage />;
}

function greeting(): string {
  const h = new Date().getHours();
  return h < 5 ? 'Late night' : h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
}

function Dashboard() {
  const { data: me } = useMe();
  const navigate = useNavigate();
  const [seeking, setSeeking] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [challengeOpen, setChallengeOpen] = useState(false);

  const stats = useQuery({ queryKey: ['lobby-stats'], queryFn: () => api.get<LobbyStats>('/api/lobby/stats'), refetchInterval: 10_000 });
  const ongoing = useQuery({ queryKey: ['ongoing'], queryFn: () => api.get<GameView[]>('/api/me/games/ongoing') });

  useEffect(() => {
    return subscribe('/user/queue/match', (body) => {
      setSeeking(null);
      navigate(`/game/${(body as { gameId: string }).gameId}`);
    });
  }, [navigate]);

  async function seek(min: number, inc: number) {
    const key = `${min}+${inc}`;
    setError(null);
    if (seeking === key) {
      setSeeking(null);
      await api.del('/api/lobby/seek').catch(() => undefined);
      return;
    }
    try {
      setSeeking(key);
      await api.post('/api/lobby/seek', { initial: min * 60, increment: inc, rated: true });
    } catch (e) {
      setSeeking(null);
      setError(errorMessage(e));
    }
  }

  if (!me) return null;
  const perfs: Perf[] = ['bullet', 'blitz', 'rapid', 'classical', 'puzzle'];

  return (
    <div className="space-y-8">
      {/* Greeting + ratings */}
      <header className="flex animate-rise flex-col justify-between gap-6 lg:flex-row lg:items-end">
        <div>
          <p className="eyebrow">{greeting()}</p>
          <h1 className="mt-2 text-[clamp(2.2rem,5vw,4rem)] leading-none font-light tracking-[-0.035em]">
            {me.username}<span className="text-ember-500">.</span>
          </h1>
        </div>
        <div className="flex flex-wrap gap-2">
          {perfs.map((p) => (
            <div key={p} className="rounded-2xl border border-white/[0.07] bg-white/[0.03] px-4 py-2.5">
              <p className="eyebrow">{p}</p>
              <p className="num text-lg text-noir-50">
                {me.ratings[p]?.rating ?? 1500}
                {(me.ratings[p]?.provisional ?? true) && <span className="text-noir-500">?</span>}
              </p>
            </div>
          ))}
        </div>
      </header>

      {ongoing.data && ongoing.data.length > 0 && (
        <section className="card flex flex-wrap items-center gap-3 border-ember-500/30 p-4">
          <span className="flex items-center gap-2 text-sm"><span className="h-2 w-2 animate-pulse rounded-full bg-ember-500" /> Your games in progress</span>
          {ongoing.data.map((g) => (
            <Link key={g.id} to={`/game/${g.id}`} className="btn-accent py-2">
              <Play size={14} className="fill-current" /> vs {g.white.id === me.id ? g.black.username : g.white.username}
            </Link>
          ))}
        </section>
      )}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        {/* ---------------------------------------------------------- play column */}
        <div className="space-y-6">
          <section className="card p-5 sm:p-7">
            <div className="flex items-center justify-between">
              <div>
                <p className="eyebrow">01/ Quick pairing</p>
                <h2 className="mt-2 text-2xl font-light tracking-tight">Find a rated game</h2>
              </div>
              {stats.data && (
                <p className="num text-right text-xs text-noir-400">
                  <span className="text-noir-100">{stats.data.online}</span> online<br />
                  <span className="text-noir-100">{Math.round(stats.data.playing / 2)}</span> games live
                </p>
              )}
            </div>
            <div className="mt-6 grid grid-cols-3 gap-2 sm:grid-cols-4">
              {POOLS.map(([min, inc]) => {
                const key = `${min}+${inc}`;
                const active = seeking === key;
                const waiting = stats.data?.waiting[key];
                return (
                  <button
                    key={key}
                    onClick={() => seek(min, inc)}
                    className={clsx(
                      'group relative flex flex-col items-start rounded-2xl border px-4 py-4 text-left transition duration-200',
                      active
                        ? 'border-ember-500/60 bg-ember-500/10 shadow-[0_0_40px_-10px_rgba(229,83,45,.6)]'
                        : 'border-white/[0.07] bg-white/[0.02] hover:-translate-y-0.5 hover:border-white/[0.16] hover:bg-white/[0.05]',
                    )}
                  >
                    <span className="num text-2xl font-light text-noir-50 sm:text-3xl">{key}</span>
                    <span className="eyebrow mt-1 flex items-center gap-1.5">
                      {active ? (
                        <><Loader2 size={11} className="animate-spin text-ember-400" /> <span className="text-ember-300">searching</span></>
                      ) : (
                        <>{perfName(min * 60, inc)}{waiting ? <span className="text-ember-400">· {waiting}</span> : null}</>
                      )}
                    </span>
                  </button>
                );
              })}
            </div>
            {seeking && <p className="mt-3 text-sm text-noir-400">Looking for an opponent near your rating. Click the tile again to cancel.</p>}
            {error && <p className="mt-3 text-sm text-loss">{error}</p>}
          </section>

          <div className="grid gap-6 md:grid-cols-[1.4fr_1fr]">
            <PlayComputer />
            <section className="card flex flex-col p-6">
              <p className="eyebrow">03/ A friend</p>
              <UserPlus className="mt-6 text-noir-300" size={28} />
              <h2 className="mt-4 text-xl font-light tracking-tight">Play someone you know</h2>
              <p className="mt-2 text-sm text-noir-400">Create a link; the first person to open it plays you.</p>
              <button className="btn-secondary mt-auto w-full" onClick={() => setChallengeOpen(true)}>
                Create challenge <ArrowRight size={15} />
              </button>
            </section>
          </div>
        </div>

        {/* ---------------------------------------------------------- training column */}
        <div className="space-y-6">
          <DailyPuzzle />
          <div className="grid grid-cols-2 gap-4">
            <Shortcut to="/learn" icon={GraduationCap} label="Lessons" text="Tactics, endgames, openings" />
            <Shortcut to="/puzzles/rush" icon={Puzzle} label="Puzzle Rush" text="3 minutes. Go." />
            <Shortcut to="/tv" icon={Tv} label="Watch" text="Top games live" />
            <Shortcut to="/tournaments" icon={Swords} label="Arenas" text="Hourly blitz" />
          </div>
        </div>
      </div>
      {challengeOpen && <ChallengeDialog onClose={() => setChallengeOpen(false)} />}
    </div>
  );
}

/** Start a game against Gambyte Engine: choose level, colour and time. */
function PlayComputer() {
  const navigate = useNavigate();
  const [level, setLevel] = useState(3);
  const [color, setColor] = useState<'white' | 'random' | 'black'>('random');
  const [time, setTime] = useState<[number, number]>([10, 0]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const info = LEVELS[level - 1];

  async function start() {
    setBusy(true);
    setError(null);
    try {
      const g = await api.post<GameView>('/api/games/computer', { level, color, initial: time[0] * 60, increment: time[1] });
      navigate(`/game/${g.id}`);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card relative overflow-hidden p-6">
      <div className="absolute -top-20 -right-20 h-56 w-56 rounded-full bg-ember-500/20 blur-3xl" />
      <div className="relative flex items-start justify-between">
        <p className="eyebrow">02/ Gambyte Engine</p>
        <Bot size={20} className="text-ember-400" />
      </div>
      <div className="relative mt-5 flex items-end justify-between">
        <div>
          <p className="text-xl font-light tracking-tight">Level {level} · {info.label}</p>
          <p className="num text-sm text-noir-400">≈ {info.rating}</p>
        </div>
        <span className="num text-5xl font-extralight text-noir-50/90">0{level}</span>
      </div>
      <input
        aria-label="Engine level"
        type="range"
        min={1}
        max={8}
        value={level}
        onChange={(e) => setLevel(Number(e.target.value))}
        className="relative mt-4 w-full"
      />
      <div className="relative mt-4 flex flex-wrap gap-1 rounded-full border border-white/[0.07] bg-white/[0.02] p-1">
        {([[3, 2], [10, 0], [15, 10], [0, 0]] as [number, number][]).map(([m, i]) => (
          <button key={`${m}+${i}`} onClick={() => setTime([m, i])} className={clsx('flex-1', time[0] === m && time[1] === i ? 'seg-on' : 'seg')}>
            {m === 0 ? '∞' : `${m}+${i}`}
          </button>
        ))}
      </div>
      <div className="relative mt-2 flex gap-1 rounded-full border border-white/[0.07] bg-white/[0.02] p-1">
        {(['white', 'random', 'black'] as const).map((c) => (
          <button key={c} onClick={() => setColor(c)} className={clsx('flex-1 capitalize', color === c ? 'seg-on' : 'seg')}>
            {c}
          </button>
        ))}
      </div>
      <button className="btn-accent relative mt-5 w-full py-3" onClick={start} disabled={busy}>
        {busy ? 'Starting…' : <>Play the engine <ArrowRight size={16} /></>}
      </button>
      {error && <p className="relative mt-2 text-sm text-loss">{error}</p>}
    </section>
  );
}

interface PuzzleData {
  id: string;
  fen: string;
  rating: number;
  themes: string[];
}

function DailyPuzzle() {
  const settings = useSettings();
  const { data } = useQuery({ queryKey: ['daily-puzzle'], queryFn: () => api.get<PuzzleData>('/api/puzzles/daily') });
  return (
    <Link to="/puzzles" className="card card-hover group block overflow-hidden p-5">
      <div className="flex items-center justify-between">
        <p className="eyebrow">Daily puzzle</p>
        <ArrowUpRight size={18} className="text-noir-400 transition group-hover:text-noir-50" />
      </div>
      <div className="mt-4 overflow-hidden rounded-xl">
        {data ? (
          <Board fen={data.fen} orientation={data.fen.split(' ')[1] === 'w' ? 'white' : 'black'} movable={null} onMove={() => false} showCoordinates={false} theme={settings.boardTheme} />
        ) : (
          <div className="aspect-square animate-pulse rounded-xl bg-white/[0.04]" />
        )}
      </div>
      {data && (
        <p className="mt-4 flex items-center justify-between text-sm">
          <span>{data.fen.split(' ')[1] === 'w' ? 'White' : 'Black'} to play and win</span>
          <span className="num text-noir-400">{data.rating}</span>
        </p>
      )}
    </Link>
  );
}

function Shortcut({ to, icon: Icon, label, text }: { to: string; icon: typeof Tv; label: string; text: string }) {
  return (
    <Link to={to} className="card card-hover group p-4">
      <Icon size={20} className="text-ember-400" />
      <p className="mt-6 font-medium">{label}</p>
      <p className="text-xs text-noir-400">{text}</p>
    </Link>
  );
}

function perfName(initialSec: number, inc: number): string {
  const est = initialSec + 40 * inc;
  if (est < 180) return 'Bullet';
  if (est < 480) return 'Blitz';
  if (est < 1500) return 'Rapid';
  return 'Classical';
}
