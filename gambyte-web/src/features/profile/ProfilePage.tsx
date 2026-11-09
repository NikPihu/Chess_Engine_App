/** A player's profile: ratings, recent games, and actions (challenge, follow, report). */
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Bot, Crown, Flag, Swords, UserCheck, UserPlus } from 'lucide-react';
import { api } from '@/lib/api';
import type { GamePage, GameSummary, Perf, Profile } from '@/lib/types';
import { Spinner } from '@/components/Spinner';
import { useMe } from '@/features/auth/useAuth';
import { ChallengeDialog } from '@/features/lobby/ChallengeDialog';
import { ReportDialog } from '@/features/social/ReportDialog';

const PERFS: Perf[] = ['bullet', 'blitz', 'rapid', 'classical', 'puzzle'];

export default function ProfilePage() {
  const { username = '' } = useParams();
  const { data: me } = useMe();
  const qc = useQueryClient();
  const [challenge, setChallenge] = useState(false);
  const [report, setReport] = useState(false);

  const profile = useQuery({
    queryKey: ['profile', username],
    queryFn: () => api.get<Profile>(`/api/users/${username}`),
  });
  const following = useQuery({
    queryKey: ['following', username],
    queryFn: () => api.get<{ following: boolean }>(`/api/social/follow/${username}`),
    enabled: Boolean(me) && me?.username !== username,
  });
  const games = useInfiniteQuery({
    queryKey: ['games', username],
    queryFn: ({ pageParam }) =>
      api.get<GamePage>(`/api/users/${username}/games?limit=20${pageParam ? `&before=${pageParam}` : ''}`),
    initialPageParam: '' as string,
    getNextPageParam: (last) => last.nextBefore ?? undefined,
  });

  if (profile.isLoading) return <Spinner />;
  if (!profile.data) return <p className="text-center text-ink-300">Player not found.</p>;
  const p = profile.data;
  const isMe = me?.username === p.username;

  async function toggleFollow() {
    if (following.data?.following) await api.del(`/api/social/follow/${p.username}`);
    else await api.post(`/api/social/follow/${p.username}`);
    void qc.invalidateQueries({ queryKey: ['following', username] });
  }

  return (
    <div className="space-y-6">
      <section className="card flex flex-wrap items-center gap-4 p-5">
        <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-ink-700 text-3xl font-bold">
          {p.title === 'BOT' ? <Bot size={32} /> : p.username[0].toUpperCase()}
        </div>
        <div className="min-w-0">
          <h1 className="flex items-center gap-2 text-2xl font-bold">
            {p.title && <span className="rounded bg-amber-500/20 px-1.5 text-sm text-amber-300">{p.title}</span>}
            {p.username}
            {p.premium && <Crown size={18} className="text-amber-300" />}
            <span className={clsx('h-2.5 w-2.5 rounded-full', p.online ? 'bg-brand-500' : 'bg-ink-600')} title={p.online ? 'Online' : 'Offline'} />
          </h1>
          {p.bio && <p className="mt-1 text-sm text-ink-300">{p.bio}</p>}
          <p className="mt-1 text-xs text-ink-400">
            {p.country && `${flag(p.country)} · `}Member since {new Date(p.createdAt).toLocaleDateString()}
          </p>
        </div>
        {me && !isMe && (
          <div className="ml-auto flex flex-wrap gap-2">
            <button className="btn-primary" onClick={() => setChallenge(true)}>
              <Swords size={16} /> Challenge
            </button>
            <button className="btn-secondary" onClick={toggleFollow}>
              {following.data?.following ? <UserCheck size={16} /> : <UserPlus size={16} />}
              {following.data?.following ? 'Following' : 'Follow'}
            </button>
            <button className="btn-ghost" onClick={() => setReport(true)} aria-label="Report">
              <Flag size={16} />
            </button>
          </div>
        )}
      </section>

      <section className="grid grid-cols-2 gap-3 sm:grid-cols-5">
        {PERFS.map((perf) => {
          const r = p.ratings[perf];
          return (
            <div key={perf} className="card p-3 text-center">
              <p className="text-xs uppercase tracking-wide text-ink-400">{perf}</p>
              <p className="text-2xl font-bold">
                {r ? r.rating : '-'}
                {r?.provisional && <span className="text-ink-400">?</span>}
              </p>
              <p className="text-xs text-ink-400">{r ? `${r.games} games · best ${r.best}` : 'no games'}</p>
            </div>
          );
        })}
      </section>

      <section className="card">
        <h2 className="border-b border-ink-700 px-4 py-3 font-semibold">Recent games</h2>
        <div className="divide-y divide-ink-700">
          {games.data?.pages.flatMap((pg) => pg.games).map((g) => <GameRow key={g.id} game={g} username={p.username} />)}
          {games.data?.pages[0]?.games.length === 0 && <p className="p-4 text-sm text-ink-400">No games yet.</p>}
        </div>
        {games.hasNextPage && (
          <button className="btn-ghost w-full" onClick={() => games.fetchNextPage()} disabled={games.isFetchingNextPage}>
            Load more
          </button>
        )}
      </section>
      {challenge && <ChallengeDialog opponent={p.username} onClose={() => setChallenge(false)} />}
      {report && <ReportDialog username={p.username} onClose={() => setReport(false)} />}
    </div>
  );
}

function GameRow({ game: g, username }: { game: GameSummary; username: string }) {
  const myColor = g.white.username === username ? 'white' : 'black';
  const opp = myColor === 'white' ? g.black : g.white;
  const result = !g.winner ? (g.status === 'ABORTED' ? 'aborted' : 'draw') : g.winner === myColor ? 'win' : 'loss';
  const diff = myColor === 'white' ? g.white.ratingDiff : g.black.ratingDiff;
  return (
    <Link to={`/game/${g.id}`} className="flex items-center gap-3 px-4 py-2.5 text-sm hover:bg-ink-800">
      <span
        className={clsx(
          'w-14 rounded px-2 py-0.5 text-center text-xs font-semibold uppercase',
          result === 'win' && 'bg-brand-600/30 text-brand-400',
          result === 'loss' && 'bg-red-600/20 text-red-300',
          (result === 'draw' || result === 'aborted') && 'bg-ink-700 text-ink-300',
        )}
      >
        {result}
      </span>
      <span className="truncate">
        vs {opp.title && <span className="text-amber-300">{opp.title} </span>}
        {opp.username} {opp.engineLevel ? `(level ${opp.engineLevel})` : opp.rating ? `(${opp.rating})` : ''}
      </span>
      <span className="ml-auto hidden text-ink-400 sm:inline">
        {g.initial === 0 ? '∞' : `${g.initial / 60}+${g.increment}`} {g.rated ? 'rated' : 'casual'} · {Math.ceil(g.ply / 2)} moves
      </span>
      {diff !== undefined && diff !== null && (
        <span className={clsx('w-10 text-right font-mono', diff >= 0 ? 'text-brand-400' : 'text-red-300')}>
          {diff >= 0 ? '+' : ''}
          {diff}
        </span>
      )}
    </Link>
  );
}

/** Country code → flag emoji ("IN" → 🇮🇳). */
function flag(code: string): string {
  return String.fromCodePoint(...[...code.toUpperCase()].map((c) => 0x1f1e6 + c.charCodeAt(0) - 65));
}
