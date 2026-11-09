/** Top 100 players of each rating pool. */
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { Crown } from 'lucide-react';
import { api } from '@/lib/api';
import type { LeaderboardEntry, Perf } from '@/lib/types';
import { Spinner } from '@/components/Spinner';
import { PageHeader } from '@/components/PageHeader';

const PERFS: Perf[] = ['bullet', 'blitz', 'rapid', 'classical', 'puzzle'];

export default function LeaderboardPage() {
  const [perf, setPerf] = useState<Perf>('blitz');
  const { data, isLoading } = useQuery({
    queryKey: ['leaderboard', perf],
    queryFn: () => api.get<{ entries: LeaderboardEntry[] }>(`/api/leaderboard/${perf}`),
  });

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader eyebrow="Rankings" title="Leaderboard" kicker="Established players only: 5+ games and a settled rating." />
      <div className="mb-6 inline-flex flex-wrap gap-1 rounded-full border border-white/[0.07] bg-white/[0.02] p-1">
        {PERFS.map((p) => (
          <button
            key={p}
            onClick={() => setPerf(p)}
            className={clsx('capitalize', perf === p ? 'seg-on' : 'seg')}
          >
            {p}
          </button>
        ))}
      </div>
      <div className="card divide-y divide-white/[0.05] overflow-hidden">
        {isLoading && <Spinner />}
        {data?.entries.length === 0 && (
          <p className="p-4 text-sm text-ink-400">Nobody qualifies yet - players need 5+ games and a settled rating.</p>
        )}
        {data?.entries.map((e) => (
          <div key={e.user.id} className="flex items-center gap-4 px-5 py-3.5 transition hover:bg-white/[0.03]">
            <span className={clsx('num w-8 text-right text-sm', e.rank <= 3 ? 'text-gold' : 'text-noir-500')}>{String(e.rank).padStart(2, '0')}</span>
            <Link to={`/@/${e.user.username}`} className="flex items-center gap-1 font-medium hover:text-ember-300">
              {e.user.title && <span className="text-xs font-bold text-gold">{e.user.title}</span>}
              {e.user.username}
              {e.user.premium && <Crown size={12} className="text-gold" />}
            </Link>
            <span className="num ml-auto text-lg">{e.rating.rating}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
