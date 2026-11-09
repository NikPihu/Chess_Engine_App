import { Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';

export default function NotFoundPage() {
  return (
    <div className="relative mx-auto flex min-h-[60vh] max-w-2xl flex-col items-center justify-center text-center">
      <div className="absolute h-72 w-72 rounded-full bg-ember-600/20 blur-[100px]" />
      <p className="eyebrow relative">Error 404</p>
      <h1 className="display relative mt-4 text-[clamp(4rem,14vw,10rem)]">Empty<br />square</h1>
      <p className="relative mt-6 text-noir-400">The page you were looking for is not on the board.</p>
      <Link to="/" className="btn-primary relative mt-8"><ArrowLeft size={16} /> Back to the lobby</Link>
    </div>
  );
}
