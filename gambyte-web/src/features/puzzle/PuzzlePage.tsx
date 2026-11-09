/** Rated puzzles at your level, with themes, hints, the daily puzzle and a link to Puzzle Rush. */
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { ArrowRight, Calendar, Lightbulb, Timer } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { Spinner } from '@/components/Spinner';
import { meKey, useMe } from '@/features/auth/useAuth';
import { PuzzleBoard, type PuzzleData, type PuzzleOutcome } from './PuzzleBoard';

interface AttemptResult {
  rating: number;
  diff: number;
  solvedTotal: number;
}

export default function PuzzlePage() {
  const { data: me } = useMe();
  const qc = useQueryClient();
  const [theme, setTheme] = useState('');
  const [puzzle, setPuzzle] = useState<PuzzleData | null>(null);
  const [result, setResult] = useState<{ outcome: PuzzleOutcome; attempt?: AttemptResult } | null>(null);
  const [hint, setHint] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const themes = useQuery({ queryKey: ['puzzle-themes'], queryFn: () => api.get<Record<string, number>>('/api/puzzles/themes') });

  const load = useCallback(async (daily = false) => {
    setResult(null);
    setHint(false);
    setError(null);
    try {
      setPuzzle(await api.get<PuzzleData>(daily ? '/api/puzzles/daily' : `/api/puzzles/next${theme ? `?theme=${theme}` : ''}`));
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [theme]);

  useEffect(() => {
    void load();
  }, [load]);

  async function done(outcome: PuzzleOutcome) {
    if (!puzzle) return;
    const solved = outcome === 'solved' && !hint; // a hint makes it count as not solved
    if (!me) return setResult({ outcome });
    try {
      const attempt = await api.post<AttemptResult>(`/api/puzzles/${puzzle.id}/attempt`, { solved });
      setResult({ outcome, attempt });
      void qc.invalidateQueries({ queryKey: meKey });
    } catch (e) {
      setResult({ outcome });
      setError(errorMessage(e));
    }
  }

  return (
    <div className="grid animate-fade gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] xl:gap-10">
      <section className="mx-auto w-full max-w-[min(92vw,calc(100vh-10rem))]">
        {puzzle ? <PuzzleBoard puzzle={puzzle} onDone={done} showHint={hint} /> : error ? <p className="text-ink-300">{error}</p> : <Spinner />}
      </section>
      <aside className="space-y-4">
        <div className="card p-4">
          <p className="eyebrow">Your puzzle rating</p>
          <p className="num mt-3 text-5xl font-extralight tracking-tight">
            {result?.attempt?.rating ?? me?.ratings.puzzle?.rating ?? 1500}
            {result?.attempt && (
              <span className={clsx('ml-3 text-xl', result.attempt.diff >= 0 ? 'text-win' : 'text-loss')}>
                {result.attempt.diff >= 0 ? '+' : ''}
                {result.attempt.diff}
              </span>
            )}
          </p>
          {puzzle && <p className="eyebrow mt-4">Puzzle {puzzle.rating} · {puzzle.plays} plays</p>}
          {!me && <p className="mt-2 text-xs text-ink-400"><Link to="/register" className="text-ember-300">Sign up</Link> to save your rating.</p>}
        </div>

        <div className="card space-y-2 p-4">
          {!result && (
            <button className="btn-secondary w-full py-3" onClick={() => setHint(true)} disabled={hint}>
              <Lightbulb size={16} /> {hint ? 'Hint shown (counts as a miss)' : 'Hint'}
            </button>
          )}
          {result && (
            <>
              <p className={clsx('text-center font-semibold', result.outcome === 'solved' ? 'text-win' : 'text-loss')}>
                {result.outcome === 'solved' ? (hint ? 'Solved with a hint' : 'Well done!') : 'Missed - keep going!'}
              </p>
              <button className="btn-primary w-full py-3" onClick={() => load()}>
                Next puzzle <ArrowRight size={16} />
              </button>
            </>
          )}
          <button className="btn-ghost w-full" onClick={() => load(true)}>
            <Calendar size={16} /> Daily puzzle
          </button>
          <Link to="/puzzles/rush" className="btn-ghost w-full">
            <Timer size={16} /> Puzzle Rush
          </Link>
          {error && <p className="text-sm text-loss">{error}</p>}
        </div>

        {themes.data && (
          <div className="card p-4">
            <h2 className="eyebrow mb-3">Themes</h2>
            <div className="flex flex-wrap gap-1">
              <ThemeChip label="All" active={theme === ''} onClick={() => setTheme('')} />
              {Object.entries(themes.data).map(([t, n]) => (
                <ThemeChip key={t} label={`${t} (${n})`} active={theme === t} onClick={() => setTheme(t)} />
              ))}
            </div>
          </div>
        )}
      </aside>
    </div>
  );
}

function ThemeChip({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button onClick={onClick} className={clsx('rounded-full px-3 py-1 text-xs', active ? 'bg-noir-50 text-noir-950' : 'border border-white/[0.07] text-noir-300 hover:border-white/20 hover:text-noir-50')}>
      {label}
    </button>
  );
}
