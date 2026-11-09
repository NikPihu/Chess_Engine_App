/**
 * A player's clock. The server sends the remaining time together with its own timestamp; we
 * count down locally from there, so the display is smooth without trusting the browser.
 *
 * Look: large monospace digits; the running clock becomes a white pill (the brand's primary
 * colour), and turns ember under ten seconds.
 */
import { useEffect, useState } from 'react';
import clsx from 'clsx';
import { formatClock } from '@/lib/chess';
import type { ClockView, Color } from '@/lib/types';
import { sound } from '@/lib/sound';

export function Clock({ clock, color, running }: { clock: ClockView; color: Color; running: boolean }) {
  const [, tick] = useState(0);
  const [base, setBase] = useState(() => ({ at: performance.now(), ms: clock[color] }));

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
  const second = Math.floor(ms / 1000);

  useEffect(() => {
    if (running && low && second >= 0) sound.lowTime();
  }, [running, low, second]);

  if (clock.initial === 0 && clock.increment === 0) return null; // untimed game

  return (
    <div
      className={clsx(
        'num min-w-[7.5rem] rounded-2xl px-4 py-2 text-right text-[clamp(1.5rem,2.4vw,2.1rem)] leading-none font-medium transition-all duration-300',
        running
          ? low
            ? 'bg-ember-500 text-white shadow-[0_0_40px_-6px_rgba(229,83,45,.8)]'
            : 'bg-noir-50 text-noir-950 shadow-[0_10px_40px_-12px_rgba(255,255,255,.5)]'
          : 'border border-white/[0.07] bg-white/[0.03] text-noir-400',
      )}
      aria-label={`${color} clock`}
    >
      {formatClock(ms)}
    </div>
  );
}
