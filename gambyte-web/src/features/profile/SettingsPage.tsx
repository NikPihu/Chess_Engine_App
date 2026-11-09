/** Account settings: board look, sounds, gameplay options, profile, password and API tokens. */
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Copy, Trash2 } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import type { Settings } from '@/lib/types';
import { meKey, useMe } from '@/features/auth/useAuth';
import { BOARD_THEMES, type BoardTheme } from '@/features/game/Board';
import { useSettings } from './useSettings';
import { PageHeader } from '@/components/PageHeader';

export default function SettingsPage() {
  const { data: me } = useMe();
  const settings = useSettings();
  const qc = useQueryClient();

  const save = useMutation({
    mutationFn: (s: Settings) => api.put<Settings>('/api/me/settings', s),
    onSuccess: (s) => qc.setQueryData(meKey, (old: typeof me) => (old ? { ...old, settings: s } : old)),
  });
  const set = (patch: Partial<Settings>) => save.mutate({ ...settings, ...patch });

  if (!me) return null;
  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <PageHeader eyebrow="Account" title="Settings" kicker="Board, sounds, profile, password and API tokens." />

      <section className="card space-y-4 p-5">
        <h2 className="font-semibold">Board</h2>
        <div className="flex gap-3">
          {(Object.keys(BOARD_THEMES) as BoardTheme[]).map((t) => (
            <button
              key={t}
              onClick={() => set({ boardTheme: t })}
              className={clsx('grid h-14 w-14 grid-cols-2 overflow-hidden rounded-lg ring-2', settings.boardTheme === t ? 'ring-brand-500' : 'ring-transparent')}
              aria-label={`${t} board`}
            >
              <span style={{ background: BOARD_THEMES[t].light }} />
              <span style={{ background: BOARD_THEMES[t].dark }} />
              <span style={{ background: BOARD_THEMES[t].dark }} />
              <span style={{ background: BOARD_THEMES[t].light }} />
            </button>
          ))}
        </div>
        <Toggle label="Show coordinates" value={settings.showCoordinates} onChange={(v) => set({ showCoordinates: v })} />
        <Toggle label="Move sounds" value={settings.sound} onChange={(v) => set({ sound: v })} />
        <Toggle label="Premoves (queue a move during the opponent's turn)" value={settings.premoves} onChange={(v) => set({ premoves: v })} />
        <Toggle label="Always promote to a queen" value={settings.autoQueen} onChange={(v) => set({ autoQueen: v })} />
      </section>

      <ProfileForm />
      <PasswordForm />
      <TokensSection />
    </div>
  );
}

function Toggle({ label, value, onChange }: { label: string; value: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="flex cursor-pointer items-center justify-between gap-4 text-sm">
      {label}
      <button
        type="button"
        role="switch"
        aria-checked={value}
        onClick={() => onChange(!value)}
        className={clsx('relative h-6 w-11 rounded-full transition', value ? 'bg-brand-600' : 'bg-ink-600')}
      >
        <span className={clsx('absolute top-0.5 h-5 w-5 rounded-full bg-white transition', value ? 'left-5' : 'left-0.5')} />
      </button>
    </label>
  );
}

function ProfileForm() {
  const { data: me } = useMe();
  const profile = useQuery({
    queryKey: ['profile', me?.username],
    queryFn: () => api.get<{ bio?: string; country?: string }>(`/api/users/${me?.username}`),
    enabled: Boolean(me),
  });
  const [bio, setBio] = useState<string | null>(null);
  const [country, setCountry] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: () =>
      api.put('/api/me/profile', {
        bio: bio ?? profile.data?.bio ?? '',
        country: (country ?? profile.data?.country ?? '').toUpperCase(),
      }),
  });

  return (
    <section className="card space-y-3 p-5">
      <h2 className="font-semibold">Profile</h2>
      <div>
        <label className="label" htmlFor="bio">About you</label>
        <textarea id="bio" className="input" maxLength={400} rows={3} value={bio ?? profile.data?.bio ?? ''} onChange={(e) => setBio(e.target.value)} />
      </div>
      <div>
        <label className="label" htmlFor="country">Country code (e.g. IN)</label>
        <input id="country" className="input w-24 uppercase" maxLength={2} value={country ?? profile.data?.country ?? ''} onChange={(e) => setCountry(e.target.value)} />
      </div>
      <button className="btn-primary" onClick={() => save.mutate()} disabled={save.isPending}>Save profile</button>
      {save.isSuccess && <span className="ml-3 text-sm text-ember-300">Saved</span>}
      {save.isError && <p className="text-sm text-loss">{errorMessage(save.error)}</p>}
    </section>
  );
}

function PasswordForm() {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '' });
  const change = useMutation({ mutationFn: () => api.post('/api/me/password', form) });
  return (
    <section className="card space-y-3 p-5">
      <h2 className="font-semibold">Password</h2>
      <input type="password" className="input" placeholder="Current password" value={form.currentPassword} onChange={(e) => setForm({ ...form, currentPassword: e.target.value })} />
      <input type="password" className="input" placeholder="New password (8+ characters)" minLength={8} value={form.newPassword} onChange={(e) => setForm({ ...form, newPassword: e.target.value })} />
      <button className="btn-secondary" onClick={() => change.mutate()} disabled={change.isPending}>Change password</button>
      {change.isSuccess && <span className="ml-3 text-sm text-ember-300">Changed</span>}
      {change.isError && <p className="text-sm text-loss">{errorMessage(change.error)}</p>}
    </section>
  );
}

interface TokenView {
  id: number;
  name: string;
  createdAt: string;
  lastUsedAt?: string;
}

/** Personal API tokens, used by bots (see the Bots page). */
function TokensSection() {
  const qc = useQueryClient();
  const tokens = useQuery({ queryKey: ['tokens'], queryFn: () => api.get<TokenView[]>('/api/me/tokens') });
  const [name, setName] = useState('');
  const [fresh, setFresh] = useState<string | null>(null);
  const create = useMutation({
    mutationFn: () => api.post<{ token: string }>('/api/me/tokens', { name: name || 'My bot' }),
    onSuccess: (t) => {
      setFresh(t.token);
      setName('');
      void qc.invalidateQueries({ queryKey: ['tokens'] });
    },
  });

  return (
    <section className="card space-y-3 p-5">
      <h2 className="font-semibold">API tokens</h2>
      <p className="text-sm text-ink-400">For bots and scripts. A token gives full access to your account - keep it secret.</p>
      {fresh && (
        <div className="rounded-lg bg-brand-600/15 p-3 text-sm">
          <p className="mb-2 text-ember-300">Copy your token now - it will not be shown again:</p>
          <div className="flex gap-2">
            <input className="input font-mono text-xs" readOnly value={fresh} />
            <button className="btn-secondary px-3" onClick={() => navigator.clipboard.writeText(fresh)} aria-label="Copy token">
              <Copy size={16} />
            </button>
          </div>
        </div>
      )}
      <div className="flex gap-2">
        <input className="input" placeholder="Token name" maxLength={60} value={name} onChange={(e) => setName(e.target.value)} />
        <button className="btn-primary" onClick={() => create.mutate()}>Create</button>
      </div>
      <ul className="divide-y divide-white/[0.06] text-sm">
        {tokens.data?.map((t) => (
          <li key={t.id} className="flex items-center justify-between py-2">
            <span>
              {t.name}
              <span className="ml-2 text-xs text-ink-400">
                created {new Date(t.createdAt).toLocaleDateString()}
                {t.lastUsedAt && ` · used ${new Date(t.lastUsedAt).toLocaleDateString()}`}
              </span>
            </span>
            <button
              className="btn-ghost px-2"
              aria-label="Revoke token"
              onClick={() => api.del(`/api/me/tokens/${t.id}`).then(() => qc.invalidateQueries({ queryKey: ['tokens'] }))}
            >
              <Trash2 size={16} />
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
