/**
 * Create a challenge: either to a named player (they get a notification) or as an open link
 * anyone can accept. Waits on the page and jumps to the game when it is accepted.
 */
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import clsx from 'clsx';
import { Copy, X } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { subscribe } from '@/lib/ws';

interface Challenge {
  id: string;
  url: string;
}

export function ChallengeDialog({ onClose, opponent }: { onClose: () => void; opponent?: string }) {
  const navigate = useNavigate();
  const [username, setUsername] = useState(opponent ?? '');
  const [time, setTime] = useState<[number, number]>([10, 0]);
  const [color, setColor] = useState<'white' | 'random' | 'black'>('random');
  const [rated, setRated] = useState(false);
  const [created, setCreated] = useState<Challenge | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!created) return;
    return subscribe('/user/queue/match', (body) => {
      const { gameId } = body as { gameId: string };
      navigate(`/game/${gameId}`);
    });
  }, [created, navigate]);

  async function create() {
    setError(null);
    try {
      const c = await api.post<Challenge>('/api/challenges', {
        username: username.trim() || null,
        initial: time[0] * 60,
        increment: time[1],
        color,
        rated,
      });
      setCreated(c);
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  const link = created ? `${location.origin}/challenge/${created.id}` : '';

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/60 p-4" onClick={onClose}>
      <div className="card w-full max-w-md p-5" onClick={(e) => e.stopPropagation()}>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-bold">Challenge a friend</h2>
          <button className="btn-ghost px-2" onClick={onClose} aria-label="Close">
            <X size={18} />
          </button>
        </div>
        {created ? (
          <div className="space-y-3">
            <p className="text-sm text-ink-300">
              {username ? `Waiting for ${username} to accept…` : 'Send this link - the first person to open it plays you:'}
            </p>
            <div className="flex gap-2">
              <input className="input font-mono text-xs" readOnly value={link} />
              <button className="btn-secondary px-3" onClick={() => navigator.clipboard.writeText(link)} aria-label="Copy link">
                <Copy size={16} />
              </button>
            </div>
            <button
              className="btn-ghost w-full"
              onClick={() => api.del(`/api/challenges/${created.id}`).finally(onClose)}
            >
              Cancel challenge
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            <div>
              <label className="label" htmlFor="opp">Opponent (empty = open link)</label>
              <input id="opp" className="input" value={username} onChange={(e) => setUsername(e.target.value)} placeholder="username" />
            </div>
            <div>
              <span className="label">Time</span>
              <div className="grid grid-cols-4 gap-1">
                {([[1, 0], [3, 2], [5, 3], [10, 0], [15, 10], [30, 0], [60, 30], [0, 0]] as [number, number][]).map(([m, i]) => (
                  <button
                    key={`${m}+${i}`}
                    onClick={() => setTime([m, i])}
                    className={clsx('rounded-lg py-1.5 text-sm', time[0] === m && time[1] === i ? 'bg-brand-600 text-white' : 'bg-ink-800 hover:bg-ink-700')}
                  >
                    {m === 0 ? '∞' : `${m}+${i}`}
                  </button>
                ))}
              </div>
            </div>
            <div className="grid grid-cols-3 gap-1">
              {(['white', 'random', 'black'] as const).map((c) => (
                <button
                  key={c}
                  onClick={() => setColor(c)}
                  className={clsx('rounded-lg py-1.5 text-sm capitalize', color === c ? 'bg-brand-600 text-white' : 'bg-ink-800 hover:bg-ink-700')}
                >
                  {c}
                </button>
              ))}
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={rated} onChange={(e) => setRated(e.target.checked)} disabled={time[0] === 0} />
              Rated
            </label>
            <button className="btn-primary w-full" onClick={create}>Create challenge</button>
            {error && <p className="text-sm text-loss">{error}</p>}
          </div>
        )}
      </div>
    </div>
  );
}
