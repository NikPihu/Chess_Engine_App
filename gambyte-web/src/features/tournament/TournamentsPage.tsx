/** Upcoming and running arena tournaments, and a form to create one. */
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { Plus, Trophy } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { Spinner } from '@/components/Spinner';
import { useMe } from '@/features/auth/useAuth';
import { PageHeader } from '@/components/PageHeader';

export interface Tournament {
  id: number;
  name: string;
  initial: number;
  increment: number;
  rated: boolean;
  startsAt: string;
  minutes: number;
  status: 'CREATED' | 'STARTED' | 'FINISHED';
  players: number;
}

export default function TournamentsPage() {
  const { data: me } = useMe();
  const navigate = useNavigate();
  const { data, isLoading } = useQuery({ queryKey: ['tournaments'], queryFn: () => api.get<Tournament[]>('/api/tournaments'), refetchInterval: 15_000 });
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ name: '', initial: 180, increment: 0, minutes: 30, startsInMinutes: 5, rated: true });
  const [error, setError] = useState<string | null>(null);

  async function create() {
    try {
      const r = await api.post<{ id: number }>('/api/tournaments', { ...form, name: form.name || `${me?.username}'s arena` });
      navigate(`/tournaments/${r.id}`);
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="flex items-center justify-between">
        <PageHeader eyebrow="Compete" title="Arenas" kicker="Join any time, play as many games as you can. Win twice in a row and points count double." />
        {me && <button className="btn-secondary" onClick={() => setCreating((c) => !c)}><Plus size={16} /> Create</button>}
      </div>

      {creating && (
        <div className="card grid gap-3 p-4 sm:grid-cols-2">
          <input className="input sm:col-span-2" placeholder="Name" maxLength={80} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <label className="text-sm">Minutes per player
            <input type="number" min={1} max={60} className="input mt-1" value={form.initial / 60} onChange={(e) => setForm({ ...form, initial: Number(e.target.value) * 60 })} />
          </label>
          <label className="text-sm">Increment (seconds)
            <input type="number" min={0} max={60} className="input mt-1" value={form.increment} onChange={(e) => setForm({ ...form, increment: Number(e.target.value) })} />
          </label>
          <label className="text-sm">Duration (minutes)
            <input type="number" min={10} max={180} className="input mt-1" value={form.minutes} onChange={(e) => setForm({ ...form, minutes: Number(e.target.value) })} />
          </label>
          <label className="text-sm">Starts in (minutes)
            <input type="number" min={0} max={1440} className="input mt-1" value={form.startsInMinutes} onChange={(e) => setForm({ ...form, startsInMinutes: Number(e.target.value) })} />
          </label>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.rated} onChange={(e) => setForm({ ...form, rated: e.target.checked })} /> Rated</label>
          <button className="btn-primary" onClick={create}>Create arena</button>
          {error && <p className="text-sm text-loss sm:col-span-2">{error}</p>}
        </div>
      )}

      {isLoading && <Spinner />}
      {data?.length === 0 && <p className="text-ink-400">No tournaments yet - the hourly arena appears at five past the hour.</p>}
      <div className="space-y-2">
        {data?.map((t) => (
          <Link key={t.id} to={`/tournaments/${t.id}`} className="card flex items-center gap-4 p-4 transition hover:border-brand-600">
            <Trophy className={t.status === 'STARTED' ? 'text-ember-300' : 'text-ink-400'} />
            <div className="min-w-0 flex-1">
              <p className="truncate font-semibold">{t.name}</p>
              <p className="text-sm text-ink-400">
                {t.initial / 60}+{t.increment} · {t.minutes} min · {t.rated ? 'rated' : 'casual'} · {t.players} players
              </p>
            </div>
            <span className={clsx('rounded px-2 py-1 text-xs font-semibold',
              t.status === 'STARTED' ? 'bg-brand-600 text-white' : t.status === 'CREATED' ? 'bg-ink-700' : 'bg-ink-800 text-ink-400')}>
              {t.status === 'STARTED' ? 'Live' : t.status === 'CREATED' ? new Date(t.startsAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Finished'}
            </span>
          </Link>
        ))}
      </div>
    </div>
  );
}
