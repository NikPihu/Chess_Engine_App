/** The vertical evaluation bar: white's share grows from the bottom as White's advantage grows. */
export function EvalBar({ cp, mate, flipped }: { cp: number; mate?: number | null; flipped?: boolean }) {
  // Same winning-chances curve as the server's Game Review (ReviewService.winChance).
  const white = mate != null ? (mate > 0 ? 100 : 0) : 50 + 50 * (2 / (1 + Math.exp(-0.00368208 * cp)) - 1);
  const label = mate != null ? `M${Math.abs(mate)}` : (Math.abs(cp) / 100).toFixed(1);
  return (
    <div className="relative h-full w-6 overflow-hidden rounded bg-ink-950" aria-label={`Evaluation ${label}`}>
      <div
        className="absolute left-0 w-full bg-ink-100 transition-all duration-500"
        style={flipped ? { top: 0, height: `${white}%` } : { bottom: 0, height: `${white}%` }}
      />
      <span
        className={`absolute left-0 w-full text-center text-[10px] font-bold ${white >= 50 ? 'text-ink-950' : 'text-ink-100'}`}
        style={(white >= 50) !== Boolean(flipped) ? { bottom: 2 } : { top: 2 }}
      >
        {label}
      </span>
    </div>
  );
}
