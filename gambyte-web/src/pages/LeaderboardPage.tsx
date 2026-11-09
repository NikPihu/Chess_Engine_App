/** Top 100 players of each rating pool. */
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { Crown } from 'lucide-react';
import { api } from '@/lib/api';
import type { LeaderboardEntry, Perf } from '@/lib/types';
import { Spinner } from '@/components/Spinner';

const PERFS: Perf[] = ['bullet', 'blitz', 'rapid', 'classical', 'puzzle'];

export default function LeaderboardPage() {
  const [perf, setPerf] = useState<Perf>('blitz');
  const { data, isLoading } = useQuery({
    queryKey: ['leaderboard', perf],
    queryFn: () => api.get<{ entries: LeaderboardEntry[] }>(`/api/leaderboard/${perf}`),
  });

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-4 text-2xl font-bold">Leaderboard</h1>
      <div className="mb-4 flex flex-wrap gap-1">
        {PERFS.map((p) => (
          <button
            key={p}
            onClick={() => setPerf(p)}
            className={clsx('rounded-lg px-3 py-1.5 text-sm capitalize', perf === p ? 'bg-brand-600 text-white' : 'bg-ink-800 hover:bg-ink-700')}
          >
            {p}
          </button>
        ))}
      </div>
      <div className="card divide-y divide-ink-700">
        {isLoading && <Spinner />}
        {data?.entries.length === 0 && (
          <p className="p-4 text-sm text-ink-400">Nobody qualifies yet - players need 5+ games and a settled rating.</p>
        )}
        {data?.entries.map((e) => (
          <div key={e.user.id} className="flex items-center gap-3 px-4 py-2.5">
            <span className={clsx('w-8 text-right font-mono', e.rank <= 3 ? 'text-amber-300' : 'text-ink-400')}>{e.rank}</span>
            <Link to={`/@/${e.user.username}`} className="flex items-center gap-1 font-medium hover:text-brand-400">
              {e.user.title && <span className="text-xs font-bold text-amber-300">{e.user.title}</span>}
              {e.user.username}
              {e.user.premium && <Crown size={12} className="text-amber-300" />}
            </Link>
            <span className="ml-auto font-mono font-semibold">{e.rating.rating}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
