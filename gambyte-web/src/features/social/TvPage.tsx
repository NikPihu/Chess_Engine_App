/** Gambyte TV: the best game being played now, plus a grid of other live games to watch. */
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Tv } from 'lucide-react';
import { api } from '@/lib/api';
import type { GameView } from '@/lib/types';
import { Spinner } from '@/components/Spinner';
import { Board } from '@/features/game/Board';
import { useSettings } from '@/features/profile/useSettings';
import { PageHeader } from '@/components/PageHeader';

export default function TvPage() {
  const settings = useSettings();
  const { data, isLoading } = useQuery({ queryKey: ['tv'], queryFn: () => api.get<GameView[]>('/api/tv'), refetchInterval: 5000 });
  if (isLoading) return <Spinner />;
  return (
    <div>
      <PageHeader eyebrow="Gambyte TV" title="Watch live" kicker="The highest-rated games being played right now."><span className="eyebrow flex items-center gap-1.5 text-ember-300"><span className="h-1.5 w-1.5 animate-pulse rounded-full bg-ember-500" /> Live</span><Tv className="text-ember-400" /></PageHeader>
      {!data?.length && <p className="text-ink-400">No games are being played right now. Start one from the lobby!</p>}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {data?.map((g) => (
          <Link key={g.id} to={`/game/${g.id}`} className="card block p-3 transition hover:border-brand-600">
            <p className="mb-2 truncate text-sm">
              <span className="font-semibold">{g.white.username}</span> ({g.white.rating ?? '?'}) vs{' '}
              <span className="font-semibold">{g.black.username}</span> ({g.black.rating ?? '?'})
            </p>
            <div className="pointer-events-none">
              <Board fen={g.fen} orientation="white" movable={null} onMove={() => false} lastMove={g.moves[g.moves.length - 1]} theme={settings.boardTheme} showCoordinates={false} />
            </div>
            <p className="mt-2 text-xs capitalize text-ink-400">{g.perf} · {g.rated ? 'rated' : 'casual'}</p>
          </Link>
        ))}
      </div>
    </div>
  );
}
