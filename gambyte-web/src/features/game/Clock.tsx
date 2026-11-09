/**
 * A player's clock. The server sends the remaining time together with its own timestamp; we
 * count down locally from there, correcting for the difference between the server's and the
 * browser's clocks, so the display is smooth without trusting the browser for anything.
 */
import { useEffect, useState } from 'react';
import clsx from 'clsx';
import { formatClock } from '@/lib/chess';
import type { ClockView, Color } from '@/lib/types';
import { sound } from '@/lib/sound';

export function Clock({ clock, color, running }: { clock: ClockView; color: Color; running: boolean }) {
  const [, tick] = useState(0);
  const [receivedAt] = useState(() => performance.now());
  const [base, setBase] = useState({ at: receivedAt, ms: clock[color] });

  useEffect(() => {
    setBase({ at: performance.now(), ms: clock[color] });
  }, [clock, color]);

  useEffect(() => {
    if (!running) return;
    const id = setInterval(() => tick((n) => n + 1), 100);
    return () => clearInterval(id);
  }, [running]);

  const ms = running ? base.ms - (performance.now() - base.at) : base.ms;
  const low = ms < 10_000 && clock.initial > 0;

  useEffect(() => {
    if (running && low && Math.floor(ms / 1000) !== Math.floor((ms + 100) / 1000)) sound.lowTime();
  }, [running, low, ms]);

  if (clock.initial === 0 && clock.increment === 0) return null; // untimed game

  return (
    <div
      className={clsx(
        'min-w-28 rounded-lg px-3 py-1.5 text-right font-mono text-2xl font-bold tabular-nums transition',
        running ? (low ? 'bg-red-600 text-white' : 'bg-ink-100 text-ink-950') : 'bg-ink-800 text-ink-300',
      )}
      aria-label={`${color} clock`}
    >
      {formatClock(ms)}
    </div>
  );
}
