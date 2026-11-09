/** A centred loading indicator used while a page or its data loads. */
export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="flex min-h-[85vh] items-center justify-center" role="status" aria-label={label}>
      <div className="h-8 w-8 animate-spin rounded-full border-2 border-ink-600 border-t-brand-500" />
    </div>
  );
}
