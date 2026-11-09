/** About bots: play Gambyte Engine, and how to connect your own engine through the Bot API. */
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Bot, Code2, Swords } from 'lucide-react';
import { api } from '@/lib/api';
import type { LeaderboardEntry } from '@/lib/types';

export default function BotsPage() {
  const site = location.origin;
  const top = useQuery({ queryKey: ['leaderboard', 'blitz'], queryFn: () => api.get<{ entries: LeaderboardEntry[] }>('/api/leaderboard/blitz') });
  const bots = top.data?.entries.filter((e) => e.user.title === 'BOT') ?? [];

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <h1 className="flex items-center gap-2 text-2xl font-bold"><Bot /> Bots</h1>

      <section className="card p-5">
        <h2 className="mb-2 flex items-center gap-2 text-lg font-semibold"><Swords size={18} /> Gambyte Engine</h2>
        <p className="text-ink-300">
          Our own engine: written in C++, with a neural network trained only on its own games and in-house analysis. It plays
          at eight levels, from beginner to full strength.
        </p>
        <div className="mt-3 flex gap-2">
          <Link to="/" className="btn-primary">Play it</Link>
          <Link to="/@/GambyteEngine" className="btn-secondary">Profile</Link>
        </div>
      </section>

      <section className="card space-y-3 p-5">
        <h2 className="flex items-center gap-2 text-lg font-semibold"><Code2 size={18} /> Connect your own bot</h2>
        <p className="text-ink-300">
          Gambyte's Bot API follows the Lichess API, so the popular <span className="font-mono">lichess-bot</span> client works unchanged.
          Your engine runs on your own machine; Gambyte never runs uploaded programs.
        </p>
        <ol className="list-decimal space-y-2 pl-5 text-sm text-ink-300">
          <li>Create a <strong>new account</strong> for the bot (it must not have played any games).</li>
          <li>Logged in as the bot, create an API token on the <Link to="/settings" className="text-brand-400">Settings</Link> page.</li>
          <li>Upgrade it: <code className="rounded bg-ink-800 px-1.5">curl -X POST -H "Authorization: Bearer TOKEN" {site}/api/bot/account/upgrade</code></li>
          <li>In lichess-bot's <span className="font-mono">config.yml</span> set <span className="font-mono">token</span> and <span className="font-mono">url: {site}/</span>, then start it.</li>
        </ol>
        <p className="text-sm text-ink-400">
          Endpoints: <span className="font-mono">/api/stream/event</span>, <span className="font-mono">/api/bot/game/stream/&#123;id&#125;</span>,{' '}
          <span className="font-mono">/api/bot/game/&#123;id&#125;/move/&#123;uci&#125;</span>, <span className="font-mono">/api/challenge/&#123;user&#125;</span> - full list in the{' '}
          <a href="/swagger-ui.html" className="text-brand-400">API docs</a>.
        </p>
      </section>

      {bots.length > 0 && (
        <section className="card">
          <h2 className="border-b border-ink-700 px-4 py-3 font-semibold">Top bots (blitz)</h2>
          {bots.map((e) => (
            <Link key={e.user.id} to={`/@/${e.user.username}`} className="flex justify-between px-4 py-2 hover:bg-ink-800">
              <span>{e.user.username}</span>
              <span className="font-mono">{e.rating.rating}</span>
            </Link>
          ))}
        </section>
      )}
    </div>
  );
}
