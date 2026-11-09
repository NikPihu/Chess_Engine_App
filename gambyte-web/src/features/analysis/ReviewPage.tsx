/**
 * Game Review: accuracy for both players, the evaluation graph, every move labelled, the coach's
 * explanation of each mistake, and (Premium, if enabled) a chat with the AI coach.
 */
import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Crown, MessageCircle, Send, Sparkles } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { replay } from '@/lib/chess';
import type { GameView } from '@/lib/types';
import { Spinner } from '@/components/Spinner';
import { Board } from '@/features/game/Board';
import { MoveList } from '@/features/game/MoveList';
import { useMe } from '@/features/auth/useAuth';
import { useSettings } from '@/features/profile/useSettings';
import { EvalBar } from './EvalBar';

interface MoveReview {
  ply: number;
  san: string;
  color: 'white' | 'black';
  classification: string;
  symbol?: string;
  evalBefore: number;
  evalAfter: number;
  bestSan?: string;
  bestLine: string[];
  winChanceLost: number;
  comment?: string;
}

interface Review {
  white: { accuracy: number; counts: Record<string, number> };
  black: { accuracy: number; counts: Record<string, number> };
  evals: number[];
  moves: MoveReview[];
  keyMoments: number[];
}

interface ReviewStatus {
  status: 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED';
  review?: Review;
  queuePosition?: number;
}

const LABEL_ORDER = ['best', 'excellent', 'good', 'inaccuracy', 'mistake', 'blunder'];
const LABEL_COLOR: Record<string, string> = {
  best: 'text-ember-300', excellent: 'text-ember-300', good: 'text-ink-300',
  inaccuracy: 'text-yellow-300', mistake: 'text-orange-400', blunder: 'text-loss',
};

export default function ReviewPage() {
  const { id = '' } = useParams();
  const { data: me } = useMe();
  const settings = useSettings();
  const qc = useQueryClient();
  const [ply, setPly] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const game = useQuery({ queryKey: ['game', id], queryFn: () => api.get<GameView>(`/api/games/${id}`) });
  const status = useQuery({
    queryKey: ['review', id],
    queryFn: async () => {
      try {
        return await api.get<ReviewStatus>(`/api/games/${id}/review`);
      } catch {
        return null;
      }
    },
    refetchInterval: (q) => (q.state.data && ['QUEUED', 'RUNNING'].includes(q.state.data.status) ? 2000 : false),
  });

  // Ask for a review automatically the first time the page is opened.
  useEffect(() => {
    if (status.isFetched && status.data === null && !error) {
      api.post<ReviewStatus>(`/api/games/${id}/review`)
        .then(() => qc.invalidateQueries({ queryKey: ['review', id] }))
        .catch((e) => setError(errorMessage(e)));
    }
  }, [status.isFetched, status.data, id, qc, error]);

  const review = status.data?.review;
  const g = game.data;
  const fen = useMemo(() => {
    if (!g) return undefined;
    const upto = ply === null ? g.moves.length : ply + 1;
    return replay(g.startFen, g.moves.slice(0, upto)).fen();
  }, [g, ply]);

  if (!g || !fen) return <Spinner />;
  if (error) {
    return (
      <div className="card mx-auto mt-10 max-w-md p-6 text-center">
        <p>{error}</p>
        {error.includes('Premium') && <Link to="/premium" className="btn-primary mt-4"><Crown size={16} /> Go Premium</Link>}
      </div>
    );
  }
  if (!review) {
    return (
      <div className="card mx-auto mt-10 max-w-md p-6 text-center">
        <Sparkles className="mx-auto mb-3 text-ember-300" />
        <p className="font-semibold">Reviewing your game…</p>
        <p className="mt-1 text-sm text-ink-400">
          {status.data?.status === 'QUEUED' && status.data.queuePosition ? `Position ${status.data.queuePosition} in the queue` : 'The engine is going through every move.'}
        </p>
        {status.data?.status === 'FAILED' && <p className="mt-2 text-sm text-loss">The review failed. Try again later.</p>}
      </div>
    );
  }

  const current = ply === null ? review.moves[review.moves.length - 1] : review.moves[ply];
  const myColor = me && (g.white.id === me.id ? 'white' : g.black.id === me.id ? 'black' : null);

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_24rem]">
      <section className="mx-auto flex w-full max-w-[min(96vw,calc(100vh-10rem))] gap-2">
        <EvalBar cp={current?.evalAfter ?? 0} flipped={myColor === 'black'} />
        <div className="min-w-0 flex-1">
          <Board
            fen={fen}
            orientation={myColor === 'black' ? 'black' : 'white'}
            movable={null}
            onMove={() => false}
            lastMove={ply === null ? g.moves[g.moves.length - 1] : g.moves[ply]}
            theme={settings.boardTheme}
          />
        </div>
      </section>

      <aside className="space-y-4">
        <div className="card grid grid-cols-2 divide-x divide-white/[0.06]">
          {(['white', 'black'] as const).map((c) => (
            <div key={c} className="p-4 text-center">
              <p className="truncate text-sm text-ink-400">{g[c].username}</p>
              <p className="text-3xl font-bold">{review[c].accuracy}%</p>
              <p className="text-xs text-ink-400">accuracy</p>
              <div className="mt-2 space-y-0.5 text-xs">
                {LABEL_ORDER.filter((l) => review[c].counts[l]).map((l) => (
                  <p key={l} className={LABEL_COLOR[l]}>{review[c].counts[l]} {l}</p>
                ))}
              </div>
            </div>
          ))}
        </div>

        <EvalGraph evals={review.evals} current={ply === null ? review.evals.length - 1 : ply + 1} onPick={(p) => setPly(p - 1 < 0 ? -1 : p - 1)} keyMoments={review.keyMoments} />

        {current && (
          <div className="card p-4">
            <p className="text-sm">
              <span className="font-mono font-semibold">{Math.ceil(current.ply / 2)}{current.color === 'white' ? '.' : '...'} {current.san}</span>{' '}
              <span className={clsx('font-semibold capitalize', LABEL_COLOR[current.classification])}>{current.classification}</span>
            </p>
            {current.comment && <p className="mt-2 text-sm text-ink-200">{current.comment}</p>}
            {current.bestSan && current.classification !== 'best' && (
              <p className="mt-2 text-xs text-ink-400">Best: <span className="font-mono">{current.bestLine.join(' ')}</span></p>
            )}
          </div>
        )}

        <div className="card p-3">
          <MoveList
            sans={g.sans}
            current={ply ?? g.sans.length - 1}
            onSelect={setPly}
            annotations={review.moves.map((m) => m.symbol ?? undefined)}
          />
        </div>

        <CoachChat gameId={id} ply={ply === null ? review.moves.length : ply + 1} />
      </aside>
    </div>
  );
}

/** A small SVG graph of the evaluation over the game; click to jump to a move. */
function EvalGraph({ evals, current, onPick, keyMoments }: { evals: number[]; current: number; onPick: (ply: number) => void; keyMoments: number[] }) {
  const w = 360;
  const h = 90;
  const toY = (cp: number) => {
    const wc = 50 + 50 * (2 / (1 + Math.exp(-0.00368208 * cp)) - 1);
    return h - (wc / 100) * h;
  };
  const step = w / Math.max(1, evals.length - 1);
  const line = evals.map((e, i) => `${i * step},${toY(e)}`).join(' ');
  return (
    <div className="card p-3">
      <svg viewBox={`0 0 ${w} ${h}`} className="w-full cursor-pointer" onClick={(e) => {
        const rect = (e.target as SVGElement).closest('svg')!.getBoundingClientRect();
        onPick(Math.round(((e.clientX - rect.left) / rect.width) * (evals.length - 1)));
      }}>
        <rect width={w} height={h} fill="#0e0f12" />
        <polygon points={`0,${h} ${line} ${w},${h}`} fill="#e8ecf1" opacity="0.85" />
        <line x1="0" x2={w} y1={h / 2} y2={h / 2} stroke="#3a404a" strokeDasharray="3 3" />
        {keyMoments.map((k) => <circle key={k} cx={k * step} cy={toY(evals[k])} r="3" fill="#f87171" />)}
        <line x1={current * step} x2={current * step} y1="0" y2={h} stroke="#22c55e" strokeWidth="2" />
      </svg>
    </div>
  );
}

/** "Ask the coach" chat (Premium, only when the server has an AI key configured). */
function CoachChat({ gameId, ply }: { gameId: string; ply: number }) {
  const { data: me } = useMe();
  const enabled = useQuery({ queryKey: ['coach-status'], queryFn: () => api.get<{ enabled: boolean }>('/api/coach/status') });
  const [q, setQ] = useState('');
  const [log, setLog] = useState<{ who: 'you' | 'coach'; text: string }[]>([]);
  const [busy, setBusy] = useState(false);
  if (!enabled.data?.enabled) return null;

  if (!me?.premium) {
    return (
      <div className="card p-4 text-sm">
        <p className="flex items-center gap-2 font-semibold"><MessageCircle size={16} /> Ask the coach</p>
        <p className="mt-1 text-ink-400">Chat with an AI coach about this game.</p>
        <Link to="/premium" className="btn-primary mt-3"><Crown size={16} /> Premium feature</Link>
      </div>
    );
  }

  async function ask() {
    if (!q.trim()) return;
    const question = q.trim();
    setQ('');
    setLog((l) => [...l, { who: 'you', text: question }]);
    setBusy(true);
    try {
      const r = await api.post<{ answer: string }>(`/api/games/${gameId}/coach`, { ply, question });
      setLog((l) => [...l, { who: 'coach', text: r.answer }]);
    } catch (e) {
      setLog((l) => [...l, { who: 'coach', text: errorMessage(e) }]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card p-3">
      <p className="mb-2 flex items-center gap-2 text-sm font-semibold"><MessageCircle size={16} /> Ask the coach</p>
      <div className="max-h-60 space-y-2 overflow-y-auto text-sm">
        {log.map((m, i) => (
          <p key={i} className={m.who === 'you' ? 'text-ink-300' : 'rounded-lg bg-ink-800 p-2'}>{m.text}</p>
        ))}
        {busy && <p className="text-ink-400">Thinking…</p>}
      </div>
      <form className="mt-2 flex gap-2" onSubmit={(e) => { e.preventDefault(); void ask(); }}>
        <input className="input" maxLength={300} placeholder="Why was this move bad?" value={q} onChange={(e) => setQ(e.target.value)} />
        <button className="btn-secondary px-3" disabled={busy} aria-label="Ask"><Send size={16} /></button>
      </form>
    </div>
  );
}
