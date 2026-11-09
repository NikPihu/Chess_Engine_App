import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="mx-auto mt-16 max-w-md text-center">
      <p className="text-6xl">♞</p>
      <h1 className="mt-4 text-2xl font-bold">This square is empty</h1>
      <p className="mt-2 text-ink-400">The page you were looking for does not exist.</p>
      <Link to="/" className="btn-primary mt-6">
        Back to the lobby
      </Link>
    </div>
  );
}
