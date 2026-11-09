/**
 * The standard page heading: a monospace index ("01/"), a large light title, an optional line of
 * context, and optional actions on the right. Same rhythm on every page.
 */
export function PageHeader({
  eyebrow,
  title,
  kicker,
  children,
}: {
  eyebrow: string;
  title: React.ReactNode;
  kicker?: React.ReactNode;
  children?: React.ReactNode;
}) {
  return (
    <header className="mb-8 flex animate-rise flex-col justify-between gap-4 border-b border-white/[0.06] pb-6 md:flex-row md:items-end">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1 className="mt-2 text-[clamp(2rem,4.6vw,3.6rem)] leading-[0.95] font-light tracking-[-0.035em]">{title}</h1>
        {kicker && <p className="mt-3 max-w-xl text-noir-400">{kicker}</p>}
      </div>
      {children && <div className="flex flex-wrap items-center gap-2">{children}</div>}
    </header>
  );
}
