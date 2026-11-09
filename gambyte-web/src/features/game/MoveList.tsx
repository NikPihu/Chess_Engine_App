/**
 * The moves in two columns (1. e4 e5  2. Nf3 Nc6 ...), monospace like a score sheet. Clicking a
 * move shows that position; `onSelect(null)` returns to the live position. Arrow keys step.
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

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.target as HTMLElement)?.tagName === 'INPUT' || (e.target as HTMLElement)?.tagName === 'TEXTAREA') return;
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
          'rounded-lg px-2.5 py-1 text-left font-mono text-[13px] transition',
          i === current ? 'bg-noir-50 text-noir-950' : 'text-noir-200 hover:bg-white/[0.06]',
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
      <div ref={ref} className="max-h-72 min-h-28 overflow-y-auto pr-1">
        {rows.length === 0 && <p className="px-2 py-3 text-sm text-noir-500">No moves yet.</p>}
        <div className="grid grid-cols-[2.25rem_1fr_1fr] gap-y-0.5">
          {rows.map((i) => (
            <div key={i} className="contents">
              <span className="py-1 pr-2 text-right font-mono text-[12px] text-noir-500">{String(i / 2 + 1).padStart(2, '0')}</span>
              {cell(i)}
              {cell(i + 1)}
            </div>
          ))}
        </div>
      </div>
      <div className="mt-3 grid grid-cols-4 gap-1 rounded-full border border-white/[0.07] bg-white/[0.02] p-1">
        <button className="btn-ghost px-0 py-1.5" onClick={() => onSelect(-1)} aria-label="First move"><ChevronFirst size={16} /></button>
        <button className="btn-ghost px-0 py-1.5" onClick={() => onSelect(Math.max(-1, current - 1))} aria-label="Previous move"><ChevronLeft size={16} /></button>
        <button className="btn-ghost px-0 py-1.5" onClick={() => onSelect(current + 1 >= sans.length - 1 ? null : current + 1)} aria-label="Next move"><ChevronRight size={16} /></button>
        <button className="btn-ghost px-0 py-1.5" onClick={() => onSelect(null)} aria-label="Latest move"><ChevronLast size={16} /></button>
      </div>
    </div>
  );
}

function annotationClass(a?: string) {
  if (a === '??') return 'ml-0.5 text-loss';
  if (a === '?') return 'ml-0.5 text-ember-300';
  if (a === '?!') return 'ml-0.5 text-gold';
  return 'ml-0.5 text-win';
}
