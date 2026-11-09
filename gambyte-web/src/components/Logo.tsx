/**
 * The Gambyte mark: a pawn cut by a "byte" notch, in a rounded square, plus an italic wordmark
 * with an ember full stop (echoing the italic "ChessPro" wordmark in the reference design).
 */
export function Logo({ small = false }: { small?: boolean }) {
  return (
    <span className="flex items-center gap-2.5">
      <svg viewBox="0 0 40 40" className={small ? 'h-7 w-7' : 'h-8 w-8'} aria-hidden>
        <defs>
          <linearGradient id="gb-mark" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#ff7a50" />
            <stop offset="1" stopColor="#c63f1c" />
          </linearGradient>
        </defs>
        <rect width="40" height="40" rx="11" fill="url(#gb-mark)" />
        <path
          d="M13 31h14v-3H13zm2-5h10l-1.7-8.2c2.3-1.1 3.7-3.3 3.7-5.8a7 7 0 0 0-14 0c0 2.5 1.4 4.7 3.7 5.8z"
          fill="#fff7f2"
        />
        <rect x="22" y="9" width="4" height="4" rx="1" fill="#c63f1c" />
      </svg>
      {!small && (
        <span className="text-[19px] font-medium tracking-tight italic">
          Gambyte<span className="text-ember-500 not-italic">.</span>
        </span>
      )}
    </span>
  );
}
