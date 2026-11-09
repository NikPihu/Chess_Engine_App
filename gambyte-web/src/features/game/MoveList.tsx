/**
 * The moves in two columns (1. e4 e5  2. Nf3 Nc6 ...). Clicking a move shows that position;
 * `onSelect(null)` returns to the live position.
 */
import { useEffect, useRef } from 'react';
import clsx from 'clsx';
import { ChevronFirst, ChevronLast, ChevronLeft, ChevronRight } from 'lucide-react';

export function MoveList({
  sans,
  current,
  onSelect,
  annotations,
}: {
  sans: string[];
  /** Index of the shown move (ply - 1); sans.length - 1 = latest. */
  current: number;
  onSelect: (ply: number | null) => void;
  /** Optional symbols per move from a game review ("?!", "?", "??", "!"). */
  annotations?: (string | undefined)[];
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    ref.current?.querySelector('[data-current="true"]')?.scrollIntoView({ block: 'nearest' });
  }, [current]);

  // Arrow keys step through the game.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.target as HTMLElement)?.tagName === 'INPUT') return;
      if (e.key === 'ArrowLeft') onSelect(Math.max(-1, current - 1));
      if (e.key === 'ArrowRight') onSelect(current + 1 >= sans.length - 1 ? null : current + 1);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [current, sans.length, onSelect]);

  const rows = [];
  for (let i = 0; i < sans.length; i += 2) rows.push(i);

  const cell = (i: number) =>
    i < sans.length ? (
      <button
        data-current={i === current}
        onClick={() => onSelect(i === sans.length - 1 ? null : i)}
        className={clsx(
          'rounded px-2 py-0.5 text-left font-mono text-sm',
          i === current ? 'bg-brand-600 text-white' : 'hover:bg-ink-700',
        )}
      >
        {sans[i]}
        {annotations?.[i] && <span className={annotationClass(annotations[i])}>{annotations[i]}</span>}
      </button>
    ) : (
      <span />
    );

  return (
    <div className="flex flex-col">
      <div ref={ref} className="max-h-72 min-h-24 overflow-y-auto">
        {rows.length === 0 && <p className="p-2 text-sm text-ink-400">No moves yet.</p>}
        <div className="grid grid-cols-[2.5rem_1fr_1fr] gap-y-0.5">
          {rows.map((i) => (
            <div key={i} className="contents">
              <span className="py-0.5 pr-2 text-right font-mono text-sm text-ink-400">{i / 2 + 1}.</span>
              {cell(i)}
              {cell(i + 1)}
            </div>
          ))}
        </div>
      </div>
      <div className="mt-2 grid grid-cols-4 gap-1">
        <button className="btn-secondary px-2" onClick={() => onSelect(-1)} aria-label="First move">
          <ChevronFirst size={16} />
        </button>
        <button className="btn-secondary px-2" onClick={() => onSelect(Math.max(-1, current - 1))} aria-label="Previous move">
          <ChevronLeft size={16} />
        </button>
        <button
          className="btn-secondary px-2"
          onClick={() => onSelect(current + 1 >= sans.length - 1 ? null : current + 1)}
          aria-label="Next move"
        >
          <ChevronRight size={16} />
        </button>
        <button className="btn-secondary px-2" onClick={() => onSelect(null)} aria-label="Latest move">
          <ChevronLast size={16} />
        </button>
      </div>
    </div>
  );
}

function annotationClass(a?: string) {
  if (a === '??') return 'ml-0.5 text-red-400';
  if (a === '?') return 'ml-0.5 text-orange-400';
  if (a === '?!') return 'ml-0.5 text-yellow-300';
  return 'ml-0.5 text-brand-400';
}
