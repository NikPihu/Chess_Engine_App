/**
 * The public landing page (visitors). Structure:
 *  1. Cinematic hero - oversized staggered headline, numbered glass tags, white pill CTA.
 *  2. Live ticker - real numbers from the server in monospace.
 *  3. "Choose your opponent" - three ways to play.
 *  4. Bento grid - review & coach, puzzles, tournaments, bots, fair play.
 *  5. The engine ladder - eight levels with ratings.
 *  6. Final call to action.
 */
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, ArrowUpRight, Bot, Brain, Play, ShieldCheck, Swords, Trophy, Users, Zap } from 'lucide-react';
import { api } from '@/lib/api';
import { Hero3D } from '@/components/hero3d/Hero3D';
import { Board } from '@/features/game/Board';

interface LobbyStats {
  online: number;
  playing: number;
}

const LEVELS = [
  ['01', 'Beginner', 600], ['02', 'Novice', 900], ['03', 'Casual', 1200], ['04', 'Club', 1500],
  ['05', 'Strong club', 1800], ['06', 'Expert', 2100], ['07', 'Master', 2400], ['08', 'Full strength', 2800],
] as const;

export default function LandingPage() {
  const stats = useQuery({ queryKey: ['lobby-stats'], queryFn: () => api.get<LobbyStats>('/api/lobby/stats'), refetchInterval: 15_000 });
  const themes = useQuery({ queryKey: ['puzzle-themes'], queryFn: () => api.get<Record<string, number>>('/api/puzzles/themes') });
  const puzzleCount = themes.data ? Math.max(...Object.values(themes.data), 0) : null;

  return (
    <div className="-mt-16">
      {/* ------------------------------------------------------------ 1. Hero */}
      <section className="relative isolate flex min-h-[100svh] items-center overflow-hidden pt-16">
        <div className="grid-lines absolute inset-0 -z-10" />
        <Hero3D />
        <div className="relative mx-auto w-full max-w-[1440px] px-5 sm:px-8">
          <h1 className="display text-[clamp(3.4rem,11.5vw,11rem)]">
            <span className="block animate-rise">Think like</span>
            <span className="block animate-rise text-right [animation-delay:120ms]">
              <span className="font-extralight text-noir-300">an</span> engine
            </span>
          </h1>

          <div className="mt-8 grid items-end gap-8 md:grid-cols-[minmax(0,22rem)_1fr_auto]">
            <div className="animate-rise space-y-5 [animation-delay:250ms]">
              <p className="text-sm leading-relaxed text-noir-300 sm:text-base">
                Live rated games, a coach that explains every mistake, and puzzles found by our own engine. Free, forever.
              </p>
              <div className="flex flex-col items-start gap-2.5">
                <span className="tag"><span className="text-noir-400">01/</span> Live rated games</span>
                <span className="tag ml-0 sm:ml-10"><span className="text-noir-400">02/</span> Engine-powered coach</span>
                <span className="tag ml-0 sm:ml-20"><span className="text-noir-400">03/</span> Puzzles &amp; lessons</span>
              </div>
            </div>
            <div />
            <div className="flex animate-rise flex-wrap items-center gap-3 [animation-delay:350ms]">
              <Link to="/tv" className="btn-secondary px-5 py-3"><Play size={15} className="fill-current" /> Watch live</Link>
              <Link to="/register" className="btn-primary px-7 py-4 text-base">
                Play now <ArrowRight size={18} />
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* ------------------------------------------------------------ 2. Ticker */}
      <section className="border-y border-white/[0.06] bg-noir-925">
        <div className="mx-auto grid max-w-[1440px] grid-cols-2 divide-white/[0.06] sm:grid-cols-4 sm:divide-x">
          <Stat label="Players online" value={stats.data?.online} live />
          <Stat label="Games in progress" value={stats.data ? Math.round(stats.data.playing / 2) : undefined} live />
          <Stat label="Engine levels" value={8} />
          <Stat label="Puzzles mined" value={puzzleCount ?? undefined} suffix="+" />
        </div>
      </section>

      {/* ------------------------------------------------------------ 3. Ways to play */}
      <section className="mx-auto max-w-[1440px] px-5 py-24 sm:px-8 lg:py-32">
        <SectionHead index="01" title="Choose your opponent" kicker="Three ways to play, one click each." />
        <div className="mt-12 grid gap-4 md:grid-cols-3">
          <WayCard icon={Swords} title="Quick pairing" text="Bullet to classical. Matched by rating in seconds, server-side clocks, lag compensation." to="/register" cta="Find a game" />
          <WayCard icon={Bot} title="Gambyte Engine" text="Our own C++ engine with a self-trained network, from level 1 to full strength." to="/register" cta="Challenge it" accent />
          <WayCard icon={Users} title="A friend" text="Send a link - the first person to open it plays you. No account needed to watch." to="/register" cta="Create a link" />
        </div>
      </section>

      {/* ------------------------------------------------------------ 4. Bento */}
      <section className="mx-auto max-w-[1440px] px-5 pb-24 sm:px-8 lg:pb-32">
        <SectionHead index="02" title="Train with intent" kicker="Every tool is built on the same engine you play against." />
        <div className="mt-12 grid auto-rows-[minmax(220px,auto)] gap-4 lg:grid-cols-6">
          {/* Review */}
          <article className="card relative overflow-hidden p-7 lg:col-span-4 lg:row-span-2">
            <span className="eyebrow">Game review</span>
            <h3 className="mt-3 max-w-md text-3xl font-light tracking-tight sm:text-4xl">Every mistake, explained in plain words.</h3>
            <p className="mt-3 max-w-md text-noir-400">Accuracy, best moves and key moments - and a coach that tells you <em>why</em>: “This allows a forced mate in 1: Qh4#.”</p>
            <EvalGraphArt />
            <div className="mt-6 flex flex-wrap gap-2">
              {[['Best', 'text-win'], ['Inaccuracy ?!', 'text-gold'], ['Mistake ?', 'text-ember-300'], ['Blunder ??', 'text-loss']].map(([t, c]) => (
                <span key={t} className={`tag ${c}`}>{t}</span>
              ))}
            </div>
          </article>
          {/* Puzzles */}
          <article className="card p-6 lg:col-span-2 lg:row-span-2">
            <span className="eyebrow">Puzzles</span>
            <h3 className="mt-3 text-2xl font-light tracking-tight">Found by our engine, rated by you.</h3>
            <div className="mt-5 overflow-hidden rounded-xl">
              <Board fen="6rk/6pp/8/6N1/8/8/8/6K1 w - - 0 1" orientation="white" movable={null} onMove={() => false} showCoordinates={false} arrows={[{ startSquare: 'g5', endSquare: 'f7', color: 'rgba(229,83,45,.85)' }]} />
            </div>
            <p className="mt-4 text-sm text-noir-400">Puzzle Rush, themes, a daily puzzle and a Glicko-2 puzzle rating.</p>
          </article>
          <Mini icon={Trophy} label="Arenas" title="Hourly blitz tournaments" text="Win twice in a row and you're on fire: points count double." />
          <Mini icon={Brain} label="Bot API" title="Bring your own engine" text="Lichess-compatible endpoints: lichess-bot works with a URL change." />
          <Mini icon={ShieldCheck} label="Fair play" title="Statistics, then humans" text="Engine-match analysis flags games; moderators decide. Never auto-bans." />
        </div>
      </section>

      {/* ------------------------------------------------------------ 5. Engine ladder */}
      <section className="relative overflow-hidden border-y border-white/[0.06] bg-noir-925 py-24 lg:py-32">
        <div className="absolute -top-40 right-0 h-96 w-96 rounded-full bg-ember-600/20 blur-[120px]" />
        <div className="relative mx-auto max-w-[1440px] px-5 sm:px-8">
          <SectionHead index="03" title="Eight levels. One engine." kicker="Start where you are. Finish where it plays at full strength." />
          <ol className="mt-12 grid grid-cols-2 gap-px overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.06] sm:grid-cols-4 lg:grid-cols-8">
            {LEVELS.map(([n, name, rating], i) => (
              <li key={n} className="group bg-noir-925 p-5 transition hover:bg-noir-850">
                <span className="eyebrow">{n}/</span>
                <p className="mt-6 text-sm text-noir-300">{name}</p>
                <p className="num mt-1 text-2xl text-noir-50">~{rating}</p>
                <div className="mt-4 h-1 rounded-full bg-white/[0.06]">
                  <div className="h-full rounded-full bg-gradient-to-r from-ember-700 to-ember-400 transition-all duration-700 group-hover:opacity-100" style={{ width: `${((i + 1) / 8) * 100}%`, opacity: 0.7 }} />
                </div>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* ------------------------------------------------------------ 6. Final CTA */}
      <section className="relative overflow-hidden px-5 py-28 text-center sm:px-8 lg:py-40">
        <div className="absolute top-1/2 left-1/2 h-[50vmin] w-[80vmin] -translate-x-1/2 -translate-y-1/2 rounded-full bg-ember-600/25 blur-[120px]" />
        <p className="eyebrow relative">Your move</p>
        <h2 className="display relative mt-6 text-[clamp(3rem,10vw,9rem)]">Make it count</h2>
        <div className="relative mt-10 flex flex-wrap justify-center gap-3">
          <Link to="/register" className="btn-primary px-8 py-4 text-base">Create a free account <ArrowRight size={18} /></Link>
          <Link to="/puzzles" className="btn-secondary px-6 py-4 text-base"><Zap size={16} /> Try a puzzle</Link>
        </div>
      </section>
    </div>
  );
}

function Stat({ label, value, suffix, live }: { label: string; value?: number; suffix?: string; live?: boolean }) {
  return (
    <div className="flex flex-col gap-2 px-5 py-6 sm:px-8">
      <span className="eyebrow flex items-center gap-2">
        {live && <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-ember-500" />}
        {label}
      </span>
      <span className="num text-3xl font-light text-noir-50 sm:text-4xl">{value === undefined ? '—' : `${value.toLocaleString()}${suffix ?? ''}`}</span>
    </div>
  );
}

function SectionHead({ index, title, kicker }: { index: string; title: string; kicker: string }) {
  return (
    <div className="flex flex-col justify-between gap-4 border-b border-white/[0.06] pb-6 md:flex-row md:items-end">
      <div>
        <span className="eyebrow">{index}/</span>
        <h2 className="mt-3 text-[clamp(2rem,4.5vw,3.6rem)] leading-none font-light tracking-[-0.03em]">{title}</h2>
      </div>
      <p className="max-w-sm text-noir-400">{kicker}</p>
    </div>
  );
}

function WayCard({ icon: Icon, title, text, to, cta, accent }: { icon: typeof Swords; title: string; text: string; to: string; cta: string; accent?: boolean }) {
  return (
    <Link to={to} className={`card card-hover group relative flex min-h-72 flex-col overflow-hidden p-7 ${accent ? 'border-ember-500/30' : ''}`}>
      {accent && <div className="absolute -top-24 -right-24 h-64 w-64 rounded-full bg-ember-500/25 blur-3xl" />}
      <span className={`relative flex h-12 w-12 items-center justify-center rounded-2xl ${accent ? 'bg-ember-500 text-white' : 'bg-white/[0.06] text-noir-100'}`}>
        <Icon size={22} />
      </span>
      <h3 className="relative mt-auto pt-10 text-2xl font-light tracking-tight">{title}</h3>
      <p className="relative mt-2 text-sm text-noir-400">{text}</p>
      <span className="relative mt-6 inline-flex items-center gap-1.5 text-sm text-noir-100">
        {cta} <ArrowUpRight size={16} className="transition group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
      </span>
    </Link>
  );
}

function Mini({ icon: Icon, label, title, text }: { icon: typeof Swords; label: string; title: string; text: string }) {
  return (
    <article className="card p-6 lg:col-span-2">
      <div className="flex items-center justify-between">
        <span className="eyebrow">{label}</span>
        <Icon size={18} className="text-ember-400" />
      </div>
      <h3 className="mt-8 text-xl font-light tracking-tight">{title}</h3>
      <p className="mt-2 text-sm text-noir-400">{text}</p>
    </article>
  );
}

/** A decorative evaluation graph (the shape of a typical game review). */
function EvalGraphArt() {
  const pts = [50, 52, 49, 55, 58, 54, 61, 57, 63, 60, 48, 35, 42, 30, 38, 26, 22, 30, 18, 12];
  const w = 600;
  const h = 150;
  const step = w / (pts.length - 1);
  const line = pts.map((p, i) => `${i * step},${h - (p / 100) * h}`).join(' ');
  return (
    <svg viewBox={`0 0 ${w} ${h}`} className="mt-8 h-auto w-full" aria-hidden>
      <defs>
        <linearGradient id="eg" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" stopColor="#ece9e4" stopOpacity="0.9" />
          <stop offset="1" stopColor="#ece9e4" stopOpacity="0.05" />
        </linearGradient>
      </defs>
      <line x1="0" x2={w} y1={h / 2} y2={h / 2} stroke="rgba(255,255,255,.1)" strokeDasharray="4 6" />
      <polygon points={`0,${h} ${line} ${w},${h}`} fill="url(#eg)" opacity="0.25" />
      <polyline points={line} fill="none" stroke="#ece9e4" strokeWidth="2" />
      {[10, 11, 15].map((i) => (
        <circle key={i} cx={i * step} cy={h - (pts[i] / 100) * h} r="5" fill="#e5532d" />
      ))}
    </svg>
  );
}
