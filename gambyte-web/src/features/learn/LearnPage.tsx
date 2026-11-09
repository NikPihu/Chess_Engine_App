/** The lesson catalogue, grouped by category, with progress. */
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { CheckCircle2, GraduationCap } from 'lucide-react';
import { api } from '@/lib/api';
import { Spinner } from '@/components/Spinner';

interface LessonSummary {
  slug: string;
  category: string;
  level: string;
  title: string;
  summary: string;
  steps: number;
  completedSteps: number;
}

export default function LearnPage() {
  const { data, isLoading } = useQuery({ queryKey: ['lessons'], queryFn: () => api.get<LessonSummary[]>('/api/lessons') });
  if (isLoading) return <Spinner />;
  const groups = new Map<string, LessonSummary[]>();
  for (const l of data ?? []) groups.set(l.category, [...(groups.get(l.category) ?? []), l]);

  return (
    <div className="space-y-8">
      <header>
        <h1 className="flex items-center gap-2 text-2xl font-bold"><GraduationCap /> Learn chess</h1>
        <p className="mt-1 text-ink-400">Short interactive lessons - read, then solve on the board.</p>
      </header>
      {[...groups.entries()].map(([category, lessons]) => (
        <section key={category}>
          <h2 className="mb-3 text-lg font-semibold">{category}</h2>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {lessons.map((l) => {
              const done = l.completedSteps >= l.steps;
              return (
                <Link key={l.slug} to={`/learn/${l.slug}`} className="card block p-4 transition hover:border-brand-600">
                  <div className="flex items-start justify-between gap-2">
                    <h3 className="font-semibold">{l.title}</h3>
                    {done && <CheckCircle2 size={18} className="shrink-0 text-brand-400" />}
                  </div>
                  <p className="mt-1 text-sm text-ink-400">{l.summary}</p>
                  <div className="mt-3 flex items-center gap-2 text-xs text-ink-400">
                    <span className="rounded bg-ink-700 px-2 py-0.5">{l.level}</span>
                    <div className="h-1.5 flex-1 overflow-hidden rounded bg-ink-700">
                      <div className="h-full bg-brand-500" style={{ width: `${(100 * l.completedSteps) / l.steps}%` }} />
                    </div>
                  </div>
                </Link>
              );
            })}
          </div>
        </section>
      ))}
    </div>
  );
}
