/** Name, rating (with the change after the game) and clock of one side. */
import { Link } from 'react-router-dom';
import clsx from 'clsx';
import { Bot } from 'lucide-react';
import type { ClockView, Color, PlayerView } from '@/lib/types';
import { Clock } from './Clock';

export function PlayerBar({
  player,
  color,
  clock,
  running,
  active,
}: {
  player: PlayerView;
  color: Color;
  clock: ClockView;
  running: boolean;
  active: boolean;
}) {
  return (
    <div className="flex items-center justify-between gap-3 py-2">
      <div className="flex min-w-0 items-center gap-2">
        <span
          className={clsx(
            'h-3 w-3 shrink-0 rounded-full border border-ink-600',
            color === 'white' ? 'bg-white' : 'bg-black',
            active && 'ring-2 ring-brand-500',
          )}
        />
        {player.title && (
          <span className="flex items-center gap-1 rounded bg-amber-500/20 px-1.5 text-xs font-bold text-amber-300">
            {player.title === 'BOT' && <Bot size={12} />}
            {player.title}
          </span>
        )}
        <Link to={`/@/${player.username}`} className="truncate font-semibold hover:text-brand-400">
          {player.username}
        </Link>
        {player.engineLevel ? (
          <span className="text-sm text-ink-400">level {player.engineLevel}</span>
        ) : (
          player.rating !== undefined && (
            <span className="text-sm text-ink-400">
              {player.rating}
              {player.provisional && '?'}
            </span>
          )
        )}
        {player.ratingDiff !== undefined && player.ratingDiff !== null && (
          <span className={clsx('text-sm font-semibold', player.ratingDiff >= 0 ? 'text-brand-400' : 'text-red-400')}>
            {player.ratingDiff >= 0 ? '+' : ''}
            {player.ratingDiff}
          </span>
        )}
      </div>
      <Clock clock={clock} color={color} running={running} />
    </div>
  );
}
