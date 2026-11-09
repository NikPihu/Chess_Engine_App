/**
 * One arena: countdown, standings, join/withdraw. While you are joined and the arena runs, new
 * games start automatically and this page jumps to them.
 */
import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Flame, Trophy } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { formatClock } from '@/lib/chess';
import { subscribe } from '@/lib/ws';
import { Spinner } from '@/components/Spinner';
import { useMe } from '@/features/auth/useAuth';
import type { Tournament } from './TournamentsPage';

interface Standing {
  rank: number;
  userId: number;
  username: string;
  score: number;
  games: number;
  onFire: boolean;
  rating: number;
  withdrawn: boolean;
}

interface Detail {
  tournament: Tournament;
  standings: Standing[];
  endsAt: string;
  joined: boolean;
}

export default function TournamentPage() {
  const { id = '' } = useParams();
  const { data: me } = useMe();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now());
  const { data } = useQuery({ queryKey: ['tournament', id], queryFn: () => api.get<Detail>(`/api/tournaments/${id}`), refetchInterval: 5000 });

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  useEffect(() => {
    if (!data?.joined) return;
    return subscribe('/user/queue/match', (body) => navigate(`/game/${(body as { gameId: string }).gameId}`));
  }, [data?.joined, navigate]);

  if (!data) return <Spinner />;
  const t = data.tournament;
  const target = t.status === 'CREATED' ? new Date(t.startsAt).getTime() : new Date(data.endsAt).getTime();

  async function act(path: 'join' | 'withdraw') {
    try {
      await api.post(`/api/tournaments/${id}/${path}`);
      void qc.invalidateQueries({ queryKey: ['tournament', id] });
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <div className="mx-auto grid max-w-5xl gap-6 md:grid-cols-[18rem_1fr]">
      <aside className="card h-fit space-y-3 p-4">
        <h1 className="flex items-center gap-2 text-xl font-bold"><Trophy className="text-amber-300" /> {t.name}</h1>
        <p className="text-sm text-ink-400">{t.initial / 60}+{t.increment} · {t.rated ? 'Rated' : 'Casual'} · {t.minutes} minutes</p>
        {t.status !== 'FINISHED' && (
          <p className="text-sm">
            {t.status === 'CREATED' ? 'Starts in ' : 'Ends in '}
            <span className="font-mono text-lg font-bold">{formatClock(target - now)}</span>
          </p>
        )}
        {t.status === 'FINISHED' && <p className="text-sm font-semibold text-amber-300">Finished - winner: {data.standings[0]?.username ?? 'nobody'}</p>}
        {me && t.status !== 'FINISHED' && (
          data.joined
            ? <button className="btn-secondary w-full" onClick={() => act('withdraw')}>Withdraw</button>
            : <button className="btn-primary w-full" onClick={() => act('join')}>Join</button>
        )}
        {!me && <Link to={`/login?next=/tournaments/${id}`} className="btn-primary w-full">Log in to join</Link>}
        {data.joined && t.status === 'STARTED' && <p className="text-xs text-ink-400">Stay on this page - your next game starts automatically.</p>}
        <p className="text-xs text-ink-400">Win 2, draw 1. After two wins in a row you are on fire 🔥 and score double.</p>
        {error && <p className="text-sm text-red-300">{error}</p>}
      </aside>
      <section className="card">
        <h2 className="border-b border-ink-700 px-4 py-3 font-semibold">Standings</h2>
        {data.standings.length === 0 && <p className="p-4 text-sm text-ink-400">No players yet.</p>}
        <table className="w-full text-sm">
          <tbody className="divide-y divide-ink-700">
            {data.standings.map((s) => (
              <tr key={s.userId} className={s.withdrawn ? 'opacity-50' : ''}>
                <td className="w-10 px-4 py-2 text-right text-ink-400">{s.rank}</td>
                <td className="px-2 py-2">
                  <Link to={`/@/${s.username}`} className="font-medium hover:text-brand-400">{s.username}</Link>{' '}
                  <span className="text-ink-400">{s.rating}</span>
                </td>
                <td className="px-2 py-2 text-ink-400">{s.games} games</td>
                <td className="px-4 py-2 text-right font-bold">
                  {s.onFire && <Flame size={14} className="mr-1 inline text-orange-400" />}
                  {s.score}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
