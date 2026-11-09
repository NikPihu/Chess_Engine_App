/** The centred card used by all login/registration screens. */
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
    <div className="mx-auto mt-8 max-w-sm">
      <div className="mb-6 flex justify-center">
        <Logo />
      </div>
      <div className="card p-6">
        <h1 className="mb-5 text-xl font-bold">{title}</h1>
        {children}
      </div>
      {footer && <div className="mt-4 text-center text-sm text-ink-400">{footer}</div>}
    </div>
  );
}

/** An inline error or success line under a form. */
export function FormMessage({ error, success }: { error?: string | null; success?: string | null }) {
  if (error) return <p className="mt-3 rounded-lg bg-red-500/10 px-3 py-2 text-sm text-red-300">{error}</p>;
  if (success)
    return <p className="mt-3 rounded-lg bg-brand-500/10 px-3 py-2 text-sm text-brand-400">{success}</p>;
  return null;
}
