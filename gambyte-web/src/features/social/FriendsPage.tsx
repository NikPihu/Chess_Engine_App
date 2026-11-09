/** People you follow, who is online, and a quick challenge button. Plus player search. */
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { Search, Swords, Users } from 'lucide-react';
import { api } from '@/lib/api';
import type { UserRef } from '@/lib/types';
import { ChallengeDialog } from '@/features/lobby/ChallengeDialog';

interface Friend {
  user: UserRef;
  online: boolean;
  mutual: boolean;
}

export default function FriendsPage() {
  const [q, setQ] = useState('');
  const [challenge, setChallenge] = useState<string | null>(null);
  const friends = useQuery({ queryKey: ['following'], queryFn: () => api.get<Friend[]>('/api/social/following'), refetchInterval: 30_000 });
  const results = useQuery({
    queryKey: ['user-search', q],
    queryFn: () => api.get<UserRef[]>(`/api/users?q=${encodeURIComponent(q)}`),
    enabled: q.length >= 2,
  });

  return (
    <div className="mx-auto grid max-w-4xl gap-6 md:grid-cols-2">
      <section className="card">
        <h1 className="flex items-center gap-2 border-b border-ink-700 px-4 py-3 font-semibold"><Users size={18} /> Following</h1>
        {friends.data?.length === 0 && <p className="p-4 text-sm text-ink-400">Follow players from their profile page.</p>}
        <ul className="divide-y divide-ink-700">
          {friends.data?.map((f) => (
            <li key={f.user.id} className="flex items-center gap-3 px-4 py-2.5">
              <span className={clsx('h-2.5 w-2.5 rounded-full', f.online ? 'bg-brand-500' : 'bg-ink-600')} />
              <Link to={`/@/${f.user.username}`} className="font-medium hover:text-brand-400">{f.user.username}</Link>
              {f.mutual && <span className="rounded bg-ink-700 px-1.5 text-xs text-ink-300">friend</span>}
              {f.online && (
                <button className="btn-ghost ml-auto px-2" onClick={() => setChallenge(f.user.username)} aria-label={`Challenge ${f.user.username}`}>
                  <Swords size={16} />
                </button>
              )}
            </li>
          ))}
        </ul>
      </section>
      <section className="card p-4">
        <h2 className="mb-3 flex items-center gap-2 font-semibold"><Search size={18} /> Find players</h2>
        <input className="input" placeholder="Type at least 2 letters" value={q} onChange={(e) => setQ(e.target.value)} />
        <ul className="mt-3 space-y-1">
          {results.data?.map((u) => (
            <li key={u.id}>
              <Link to={`/@/${u.username}`} className="block rounded px-2 py-1.5 hover:bg-ink-800">
                {u.title && <span className="mr-1 text-xs font-bold text-amber-300">{u.title}</span>}
                {u.username}
              </Link>
            </li>
          ))}
        </ul>
      </section>
      {challenge && <ChallengeDialog opponent={challenge} onClose={() => setChallenge(null)} />}
    </div>
  );
}
