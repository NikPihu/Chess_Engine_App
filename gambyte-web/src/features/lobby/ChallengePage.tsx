/** Opened from a challenge link: shows the offer and lets you accept it. */
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api, errorMessage } from '@/lib/api';
import { timeControlLabel } from '@/lib/chess';
import { Spinner } from '@/components/Spinner';
import { useMe } from '@/features/auth/useAuth';

interface Challenge {
  id: string;
  challenger: string;
  dest?: string;
  initial: number;
  increment: number;
  rated: boolean;
  color: string;
}

export default function ChallengePage() {
  const { id = '' } = useParams();
  const { data: me } = useMe();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const challenge = useQuery({ queryKey: ['challenge', id], queryFn: () => api.get<Challenge>(`/api/challenges/${id}`) });

  if (challenge.isLoading) return <Spinner />;
  if (!challenge.data) {
    return (
      <div className="mx-auto mt-12 max-w-md text-center">
        <p className="text-lg">This challenge has expired or was already accepted.</p>
        <Link to="/" className="btn-primary mt-4">Back to the lobby</Link>
      </div>
    );
  }
  const c = challenge.data;
  const yourColor = c.color === 'white' ? 'black' : c.color === 'black' ? 'white' : 'random';

  async function accept() {
    try {
      const r = await api.post<{ gameId: string }>(`/api/challenges/${id}/accept`);
      navigate(`/game/${r.gameId}`);
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <div className="card mx-auto mt-12 max-w-md p-6 text-center">
      <p className="text-sm text-ink-400">Challenge from</p>
      <h1 className="text-2xl font-bold">{c.challenger}</h1>
      <p className="mt-3 text-ink-300">
        {timeControlLabel(c.initial * 1000, c.increment * 1000)} · {c.rated ? 'Rated' : 'Casual'} · you play {yourColor}
      </p>
      {me ? (
        me.username === c.challenger ? (
          <p className="mt-4 text-sm text-ink-400">Waiting for someone to open your link…</p>
        ) : (
          <button className="btn-primary mt-6 w-full" onClick={accept}>Accept and play</button>
        )
      ) : (
        <Link to={`/login?next=/challenge/${id}`} className="btn-primary mt-6 w-full">Log in to accept</Link>
      )}
      {error && <p className="mt-3 text-sm text-red-300">{error}</p>}
    </div>
  );
}
