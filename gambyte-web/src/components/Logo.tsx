/** The Gambyte word mark: a pawn in a rounded square plus the name. */
export function Logo({ small = false }: { small?: boolean }) {
  return (
    <span className="flex items-center gap-2">
      <svg viewBox="0 0 64 64" className={small ? 'h-6 w-6' : 'h-8 w-8'} aria-hidden>
        <rect width="64" height="64" rx="14" fill="#16a34a" />
        <path
          d="M20 50h24v-5H20zm3-8h18l-3-14c4-2 6-6 6-10a12 12 0 0 0-24 0c0 4 2 8 6 10z"
          fill="#f8fafc"
        />
        <circle cx="38" cy="16" r="3" fill="#16a34a" />
      </svg>
      {!small && (
        <span className="text-lg font-extrabold tracking-tight">
          Gam<span className="text-brand-400">byte</span>
        </span>
      )}
    </span>
  );
}
