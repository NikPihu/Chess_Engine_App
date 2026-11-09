/** Moderation dashboard: site numbers, fair-play flags, player reports, user actions. */
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Shield } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { useMe } from '@/features/auth/useAuth';

type Row = Record<string, string | number | boolean | null>;

export default function AdminPage() {
  const { data: me } = useMe();
  const qc = useQueryClient();
  const [tab, setTab] = useState<'flags' | 'reports' | 'users'>('flags');
  const stats = useQuery({ queryKey: ['admin-stats'], queryFn: () => api.get<Row>('/api/admin/stats') });
  const flags = useQuery({ queryKey: ['admin-flags'], queryFn: () => api.get<Row[]>('/api/admin/flags'), enabled: tab === 'flags' });
  const reports = useQuery({ queryKey: ['admin-reports'], queryFn: () => api.get<Row[]>('/api/admin/reports'), enabled: tab === 'reports' });
  const [username, setUsername] = useState('');
  const [msg, setMsg] = useState<string | null>(null);

  async function act(path: string) {
    setMsg(null);
    try {
      await api.post(path);
      setMsg('Done');
      void qc.invalidateQueries();
    } catch (e) {
      setMsg(errorMessage(e));
    }
  }

  return (
    <div className="space-y-6">
      <h1 className="flex items-center gap-2 text-2xl font-bold"><Shield /> Moderation</h1>
      {stats.data && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
          {Object.entries(stats.data).map(([k, v]) => (
            <div key={k} className="card p-3">
              <p className="text-xs uppercase text-ink-400">{k.replaceAll('_', ' ')}</p>
              <p className="text-2xl font-bold">{String(v)}</p>
            </div>
          ))}
        </div>
      )}
      <div className="flex gap-1">
        {(['flags', 'reports', 'users'] as const).map((t) => (
          <button key={t} onClick={() => setTab(t)} className={clsx('rounded-lg px-3 py-1.5 text-sm capitalize', tab === t ? 'bg-brand-600 text-white' : 'bg-ink-800')}>{t}</button>
        ))}
      </div>

      {tab === 'flags' && (
        <div className="card overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-left text-xs uppercase text-ink-400">
              <tr><th className="p-3">Player</th><th>Game</th><th>Suspicion</th><th>Engine match</th><th>ACPL</th><th>Time CV</th><th>Blurs</th><th>Flags</th><th /></tr>
            </thead>
            <tbody className="divide-y divide-ink-700">
              {flags.data?.map((f) => (
                <tr key={`${f.game_id}-${f.user_id}`}>
                  <td className="p-3"><Link to={`/@/${f.username}`} className="text-brand-400">{f.username}</Link> ({f.rating})</td>
                  <td><Link to={`/game/${f.game_id}`} className="font-mono text-brand-400">{f.game_id}</Link></td>
                  <td className="font-bold text-red-300">{Math.round(Number(f.suspicion) * 100)}%</td>
                  <td>{Math.round(Number(f.top_match) * 100)}%</td>
                  <td>{f.acpl}</td>
                  <td>{f.time_cv}</td>
                  <td>{f.blurs}</td>
                  <td>{f.total_flags}</td>
                  <td className="space-x-1 whitespace-nowrap pr-3">
                    <button className="btn-ghost px-2 text-xs" onClick={() => act(`/api/admin/flags/${f.game_id}/${f.user_id}/reviewed`)}>Dismiss</button>
                    <button className="btn bg-red-700 px-2 py-1 text-xs text-white" onClick={() => act(`/api/admin/users/${f.username}/ban`)}>Close account</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {flags.data?.length === 0 && <p className="p-4 text-sm text-ink-400">No open flags.</p>}
        </div>
      )}

      {tab === 'reports' && (
        <div className="space-y-2">
          {reports.data?.length === 0 && <p className="text-sm text-ink-400">No open reports.</p>}
          {reports.data?.map((r) => (
            <div key={String(r.id)} className="card p-4 text-sm">
              <p>
                <span className="font-semibold">{r.reporter}</span> reported <Link to={`/@/${r.reported}`} className="text-brand-400">{r.reported}</Link> for{' '}
                <span className="font-semibold">{r.reason}</span>{r.game_id && <> in <Link to={`/game/${r.game_id}`} className="font-mono text-brand-400">{r.game_id}</Link></>}
              </p>
              {r.text && <p className="mt-1 text-ink-300">{r.text}</p>}
              <div className="mt-2 flex gap-2">
                <button className="btn-secondary px-2 py-1 text-xs" onClick={() => api.post(`/api/admin/reports/${r.id}/close`, { resolution: 'no action' }).then(() => qc.invalidateQueries())}>Close, no action</button>
                <button className="btn-secondary px-2 py-1 text-xs" onClick={() => act(`/api/admin/users/${r.reported}/mute`)}>Mute</button>
                <button className="btn bg-red-700 px-2 py-1 text-xs text-white" onClick={() => act(`/api/admin/users/${r.reported}/ban`)}>Close account</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {tab === 'users' && (
        <div className="card max-w-xl space-y-3 p-4">
          <input className="input" placeholder="Username" value={username} onChange={(e) => setUsername(e.target.value)} />
          <div className="flex flex-wrap gap-2">
            {['ban', 'unban', 'mute', 'unmute'].map((a) => (
              <button key={a} className="btn-secondary capitalize" disabled={!username} onClick={() => act(`/api/admin/users/${username}/${a}`)}>{a}</button>
            ))}
            {me?.role === 'ADMIN' && (
              <>
                <button className="btn-secondary" disabled={!username} onClick={() => act(`/api/admin/users/${username}/premium?days=30`)}>+30 days Premium</button>
                <button className="btn-secondary" disabled={!username} onClick={() => act(`/api/admin/users/${username}/role?role=MOD`)}>Make moderator</button>
              </>
            )}
          </div>
          {msg && <p className="text-sm text-ink-300">{msg}</p>}
        </div>
      )}
    </div>
  );
}
