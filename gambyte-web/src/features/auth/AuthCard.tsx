/**
 * The frame for login / registration screens: a split layout with a cinematic panel (big type,
 * ember glow, the brand's grid) on the left and the form on the right; on phones only the form.
 */
import { Logo } from '@/components/Logo';

export function AuthCard({
  title,
  children,
  footer,
}: {
  title: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}) {
  return (
    <div className="mx-auto grid max-w-5xl animate-fade overflow-hidden rounded-3xl border border-white/[0.07] bg-noir-900/60 lg:mt-6 lg:grid-cols-2">
      <aside className="relative hidden min-h-[560px] overflow-hidden p-10 lg:flex lg:flex-col">
        <div className="grid-lines absolute inset-0" />
        <div className="absolute -bottom-32 -left-20 h-96 w-96 rounded-full bg-ember-600/35 blur-[100px]" />
        <div className="relative"><Logo /></div>
        <h2 className="display relative mt-auto text-[clamp(3rem,6vw,5.5rem)]">
          Your
          <br />
          move<span className="text-ember-500">.</span>
        </h2>
        <p className="relative mt-6 max-w-xs text-sm text-noir-400">
          Live games, an engine that explains your mistakes, and puzzles mined from real games.
        </p>
      </aside>
      <div className="flex flex-col justify-center p-7 sm:p-10">
        <div className="mb-8 lg:hidden"><Logo /></div>
        <p className="eyebrow">Gambyte</p>
        <h1 className="mt-2 mb-7 text-3xl font-light tracking-tight">{title}</h1>
        {children}
        {footer && <div className="mt-8 text-sm text-noir-400">{footer}</div>}
      </div>
    </div>
  );
}

/** An inline error or success line under a form. */
export function FormMessage({ error, success }: { error?: string | null; success?: string | null }) {
  if (error) return <p className="mt-4 rounded-xl border border-loss/20 bg-loss/10 px-4 py-2.5 text-sm text-loss">{error}</p>;
  if (success)
    return <p className="mt-4 rounded-xl border border-win/20 bg-win/10 px-4 py-2.5 text-sm text-win">{success}</p>;
  return null;
}
