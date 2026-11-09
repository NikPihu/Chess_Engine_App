/** One lesson: text steps to read and exercises to solve, one at a time. */
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Chess } from 'chess.js';
import { ArrowLeft, ArrowRight, Lightbulb } from 'lucide-react';
import { api } from '@/lib/api';
import { Spinner } from '@/components/Spinner';
import { Board } from '@/features/game/Board';
import { useMe } from '@/features/auth/useAuth';
import { useSettings } from '@/features/profile/useSettings';
import { playMoveSound, sound } from '@/lib/sound';

interface Step {
  type: 'text' | 'exercise';
  text?: string;
  fen?: string;
  prompt?: string;
  accept?: string[];
  hint?: string;
  success?: string;
}

interface Lesson {
  slug: string;
  title: string;
  steps: Step[];
}

export default function LessonPage() {
  const { slug = '' } = useParams();
  const { data: me } = useMe();
  const { data: lesson, isLoading } = useQuery({ queryKey: ['lesson', slug], queryFn: () => api.get<Lesson>(`/api/lessons/${slug}`) });
  const [index, setIndex] = useState(0);
  const [solved, setSolved] = useState(false);

  if (isLoading || !lesson) return <Spinner />;
  const step = lesson.steps[index];
  const canNext = step.type === 'text' || solved;

  function next() {
    const completed = index + 1;
    if (me) void api.post(`/api/lessons/${slug}/progress`, { completedSteps: completed }).catch(() => undefined);
    setSolved(false);
    setIndex(completed);
  }

  if (index >= lesson.steps.length) {
    return (
      <div className="card mx-auto mt-10 max-w-md p-6 text-center">
        <p className="text-5xl">🎉</p>
        <h1 className="mt-3 text-3xl font-light tracking-tight">Lesson complete</h1>
        <p className="mt-2 text-ink-400">{lesson.title}</p>
        <Link to="/learn" className="btn-primary mt-6">More lessons</Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl">
      <Link to="/learn" className="btn-ghost mb-3 px-2"><ArrowLeft size={16} /> All lessons</Link>
      <h1 className="text-[clamp(1.8rem,3.5vw,2.8rem)] font-light tracking-tight">{lesson.title}</h1>
      <p className="mb-4 text-sm text-ink-400">Step {index + 1} of {lesson.steps.length}</p>
      {step.type === 'text' ? (
        <div className="card p-6">
          <Prose text={step.text ?? ''} />
        </div>
      ) : (
        <Exercise key={index} step={step} onSolved={() => setSolved(true)} />
      )}
      <div className="mt-4 flex justify-end">
        <button className="btn-primary" disabled={!canNext} onClick={next}>
          Continue <ArrowRight size={16} />
        </button>
      </div>
    </div>
  );
}

function Exercise({ step, onSolved }: { step: Step; onSolved: () => void }) {
  const settings = useSettings();
  const [fen, setFen] = useState(step.fen!);
  const [status, setStatus] = useState<'try' | 'wrong' | 'right'>('try');
  const [hint, setHint] = useState(false);
  const color = new Chess(step.fen!).turn() === 'w' ? 'white' : 'black';

  function onMove(uci: string) {
    const chess = new Chess(step.fen!);
    try {
      const m = chess.move({ from: uci.slice(0, 2), to: uci.slice(2, 4), promotion: uci[4] ?? 'q' });
      playMoveSound(m.san);
    } catch {
      return false;
    }
    const full = uci.length === 4 && step.accept!.some((a) => a.startsWith(uci) && a.length === 5) ? uci + 'q' : uci;
    if (step.accept!.includes(full)) {
      setFen(chess.fen());
      setStatus('right');
      sound.end();
      onSolved();
      return true;
    }
    setStatus('wrong');
    return false;
  }

  return (
    <div className="grid gap-6 md:grid-cols-[minmax(0,1fr)_18rem]">
      <div className="mx-auto w-full max-w-[min(92vw,calc(100vh-14rem))]">
        <Board fen={fen} orientation={color} movable={status === 'right' ? null : color} onMove={onMove} theme={settings.boardTheme} premoves={false} autoQueen />
      </div>
      <div className="card h-fit space-y-3 p-4">
        <p className="font-medium">{step.prompt}</p>
        {status === 'wrong' && <p className="text-sm text-loss">Not quite - try again.</p>}
        {status === 'right' && <p className="text-sm text-ember-300">{step.success}</p>}
        {status !== 'right' && (hint ? <p className="text-sm text-ink-300">{step.hint}</p> : (
          <button className="btn-ghost px-2" onClick={() => setHint(true)}><Lightbulb size={16} /> Hint</button>
        ))}
      </div>
    </div>
  );
}

/** Tiny Markdown subset: paragraphs, **bold**, *italic*, numbered lines. */
function Prose({ text }: { text: string }) {
  return (
    <div className="space-y-3 leading-relaxed text-ink-100">
      {text.split('\n\n').map((para, i) => (
        <p key={i} className="whitespace-pre-line">
          {para.split(/(\*\*[^*]+\*\*|\*[^*]+\*)/g).map((part, j) =>
            part.startsWith('**') ? <strong key={j}>{part.slice(2, -2)}</strong>
              : part.startsWith('*') ? <em key={j}>{part.slice(1, -1)}</em> : part,
          )}
        </p>
      ))}
    </div>
  );
}
