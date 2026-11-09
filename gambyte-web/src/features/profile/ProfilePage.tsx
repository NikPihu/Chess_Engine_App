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
      <section className="card relative flex flex-wrap items-center gap-6 overflow-hidden p-6 sm:p-8">
        <div className="pointer-events-none absolute -top-24 -left-10 h-64 w-64 rounded-full bg-ember-600/20 blur-3xl" />
        <div className="relative flex h-20 w-20 items-center justify-center rounded-3xl bg-gradient-to-br from-ember-400 to-ember-700 text-4xl font-light text-white shadow-[0_20px_50px_-15px_rgba(229,83,45,.7)]">
          {p.title === 'BOT' ? <Bot size={32} /> : p.username[0].toUpperCase()}
        </div>
        <div className="relative min-w-0">
          <p className="eyebrow">{p.title === 'BOT' ? 'Bot account' : 'Player'}</p>
          <h1 className="mt-1 flex items-center gap-3 text-[clamp(2rem,4vw,3rem)] leading-none font-light tracking-tight">
            {p.title && <span className="rounded-md bg-gold/15 px-2 py-0.5 font-mono text-xs font-bold tracking-wider text-gold">{p.title}</span>}
            {p.username}
            {p.premium && <Crown size={18} className="text-gold" />}
            <span className={clsx('h-2.5 w-2.5 rounded-full', p.online ? 'bg-win shadow-[0_0_12px_rgba(111,207,151,.8)]' : 'bg-noir-600')} title={p.online ? 'Online' : 'Offline'} />
          </h1>
          {p.bio && <p className="mt-1 text-sm text-ink-300">{p.bio}</p>}
          <p className="eyebrow mt-3">
            {p.country && `${flag(p.country)} · `}Member since {new Date(p.createdAt).toLocaleDateString()}
          </p>
        </div>
        {me && !isMe && (
          <div className="relative ml-auto flex flex-wrap gap-2">
            <button className="btn-accent" onClick={() => setChallenge(true)}>
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
            <div key={perf} className="card p-5">
              <p className="eyebrow">{perf}</p>
              <p className="num mt-3 text-3xl font-light">
                {r ? r.rating : '-'}
                {r?.provisional && <span className="text-ink-400">?</span>}
              </p>
              <p className="text-xs text-ink-400">{r ? `${r.games} games · best ${r.best}` : 'no games'}</p>
            </div>
          );
        })}
      </section>

      <section className="card">
        <h2 className="eyebrow border-b border-white/[0.06] px-5 py-4">Recent games</h2>
        <div className="divide-y divide-white/[0.06]">
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
    <Link to={`/game/${g.id}`} className="flex items-center gap-4 px-5 py-3 text-sm transition hover:bg-white/[0.03]">
      <span
        className={clsx(
          'w-16 rounded-full px-2 py-1 text-center font-mono text-[10px] font-semibold tracking-wider uppercase',
          result === 'win' && 'bg-win/15 text-win',
          result === 'loss' && 'bg-loss/15 text-loss',
          (result === 'draw' || result === 'aborted') && 'bg-ink-700 text-ink-300',
        )}
      >
        {result}
      </span>
      <span className="truncate">
        vs {opp.title && <span className="text-gold">{opp.title} </span>}
        {opp.username} {opp.engineLevel ? `(level ${opp.engineLevel})` : opp.rating ? `(${opp.rating})` : ''}
      </span>
      <span className="ml-auto hidden text-ink-400 sm:inline">
        {g.initial === 0 ? '∞' : `${g.initial / 60}+${g.increment}`} {g.rated ? 'rated' : 'casual'} · {Math.ceil(g.ply / 2)} moves
      </span>
      {diff !== undefined && diff !== null && (
        <span className={clsx('w-10 text-right font-mono', diff >= 0 ? 'text-ember-300' : 'text-loss')}>
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
