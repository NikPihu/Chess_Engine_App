/** Avatar, name, rating (with the change after the game) and clock of one side. */
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
  const bot = player.title === 'BOT';
  return (
    <div className="flex items-center justify-between gap-3 py-3">
      <div className="flex min-w-0 items-center gap-3">
        <span
          className={clsx(
            'relative flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-sm font-semibold uppercase ring-2 transition',
            color === 'white' ? 'bg-noir-100 text-noir-900' : 'bg-noir-800 text-noir-100',
            active ? 'ring-ember-500' : 'ring-transparent',
          )}
        >
          {bot ? <Bot size={18} /> : player.username[0]}
          {active && <span className="absolute -top-1 -right-1 h-2.5 w-2.5 animate-pulse rounded-full bg-ember-500" />}
        </span>
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            {player.title && (
              <span className="rounded-md bg-gold/15 px-1.5 py-0.5 font-mono text-[10px] font-bold tracking-wider text-gold">{player.title}</span>
            )}
            <Link to={`/@/${player.username}`} className="truncate font-medium hover:text-ember-300">
              {player.username}
            </Link>
          </div>
          <p className="num text-xs text-noir-400">
            {player.engineLevel ? `LEVEL ${player.engineLevel}` : player.rating !== undefined ? `${player.rating}${player.provisional ? '?' : ''}` : ''}
            {player.ratingDiff !== undefined && player.ratingDiff !== null && (
              <span className={clsx('ml-2', player.ratingDiff >= 0 ? 'text-win' : 'text-loss')}>
                {player.ratingDiff >= 0 ? '+' : ''}
                {player.ratingDiff}
              </span>
            )}
          </p>
        </div>
      </div>
      <Clock clock={clock} color={color} running={running} />
    </div>
  );
}
