/**
 * Puzzle Rush: solve as many puzzles as you can, easy to hard. "3 minutes" ends when time is up,
 * "Survival" ends after three mistakes (in both modes, three strikes end the run).
 */
import { useEffect, useRef, useState } from 'react';
import clsx from 'clsx';
import { Check, Timer, X } from 'lucide-react';
import { api } from '@/lib/api';
import { formatClock } from '@/lib/chess';
import { PuzzleBoard, type PuzzleData } from './PuzzleBoard';

type Mode = '3min' | 'survival';

export default function PuzzleRushPage() {
  const [mode, setMode] = useState<Mode>('3min');
  const [puzzles, setPuzzles] = useState<PuzzleData[] | null>(null);
  const [index, setIndex] = useState(0);
  const [results, setResults] = useState<boolean[]>([]);
  const [endsAt, setEndsAt] = useState(0);
  const [now, setNow] = useState(Date.now());
  const [over, setOver] = useState(false);
  const [best, setBest] = useState<number | null>(null);
  const saved = useRef(false);

  const score = results.filter(Boolean).length;
  const strikes = results.filter((r) => !r).length;

  useEffect(() => {
    void api.get<{ best: number }>(`/api/puzzles/rush/best?mode=${mode}`).then((r) => setBest(r.best)).catch(() => undefined);
  }, [mode]);

  useEffect(() => {
    if (!puzzles || over || mode !== '3min') return;
    const id = setInterval(() => setNow(Date.now()), 200);
    return () => clearInterval(id);
  }, [puzzles, over, mode]);

  const timeLeft = endsAt - now;
  useEffect(() => {
    if (puzzles && !over && (strikes >= 3 || (mode === '3min' && timeLeft <= 0) || index >= puzzles.length)) setOver(true);
  }, [strikes, timeLeft, mode, puzzles, over, index]);

  useEffect(() => {
    if (!over || saved.current) return;
    saved.current = true;
    void api.post<{ best: number }>('/api/puzzles/rush/score', { score, mode }).then((r) => setBest(r.best)).catch(() => undefined);
  }, [over, score, mode]);

  async function start() {
    saved.current = false;
    setResults([]);
    setIndex(0);
    setOver(false);
    setPuzzles(await api.get<PuzzleData[]>('/api/puzzles/rush'));
    setEndsAt(Date.now() + 3 * 60_000);
    setNow(Date.now());
  }

  if (!puzzles || over) {
    return (
      <div className="card mx-auto mt-8 max-w-md p-6 text-center">
        <h1 className="flex items-center justify-center gap-2 text-2xl font-bold"><Timer /> Puzzle Rush</h1>
        {over && <p className="mt-4 text-5xl font-extrabold text-brand-400">{score}</p>}
        {best !== null && <p className="mt-2 text-sm text-ink-400">Your best ({mode === '3min' ? '3 minutes' : 'survival'}): {best}</p>}
        <div className="mt-5 grid grid-cols-2 gap-2">
          {(['3min', 'survival'] as Mode[]).map((m) => (
            <button key={m} onClick={() => setMode(m)} className={clsx('rounded-lg py-2 text-sm', mode === m ? 'bg-brand-600 text-white' : 'bg-ink-800 hover:bg-ink-700')}>
              {m === '3min' ? '3 minutes' : 'Survival'}
            </button>
          ))}
        </div>
        <button className="btn-primary mt-4 w-full" onClick={start}>{over ? 'Play again' : 'Start'}</button>
      </div>
    );
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_18rem]">
      <section className="mx-auto w-full max-w-[min(92vw,calc(100vh-10rem))]">
        <PuzzleBoard
          key={puzzles[index].id}
          puzzle={puzzles[index]}
          onDone={(o) => {
            setResults((r) => [...r, o === 'solved']);
            setTimeout(() => setIndex((i) => i + 1), 500);
          }}
        />
      </section>
      <aside className="card h-fit space-y-4 p-4">
        <div className="flex items-center justify-between">
          <span className="text-4xl font-extrabold">{score}</span>
          {mode === '3min' && <span className="font-mono text-2xl">{formatClock(timeLeft)}</span>}
        </div>
        <div className="flex gap-1">
          {[0, 1, 2].map((i) => (
            <span key={i} className={clsx('flex h-8 w-8 items-center justify-center rounded', i < strikes ? 'bg-red-600' : 'bg-ink-700')}>
              <X size={16} />
            </span>
          ))}
        </div>
        <div className="flex flex-wrap gap-1">
          {results.map((r, i) => (
            <span key={i} className={clsx('flex h-6 w-6 items-center justify-center rounded', r ? 'bg-brand-600' : 'bg-red-600')}>
              {r ? <Check size={14} /> : <X size={14} />}
            </span>
          ))}
        </div>
      </aside>
    </div>
  );
}
