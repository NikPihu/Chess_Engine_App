import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { errorMessage } from '@/lib/api';
import { AuthCard, FormMessage } from './AuthCard';
import { useLogin } from './useAuth';

/** Log in with username (or email) and password. */
export default function LoginPage() {
  const login = useLogin();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [form, setForm] = useState({ login: '', password: '' });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    login.mutate(form, { onSuccess: () => navigate(params.get('next') ?? '/') });
  };

  return (
    <AuthCard
      title="Log in"
      footer={
        <>
          New here?{' '}
          <Link to="/register" className="text-brand-400 hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-4">
        <div>
          <label className="label" htmlFor="login">
            Username or email
          </label>
          <input
            id="login"
            className="input"
            autoComplete="username"
            value={form.login}
            onChange={(e) => setForm({ ...form, login: e.target.value })}
            required
          />
        </div>
        <div>
          <label className="label" htmlFor="password">
            Password
          </label>
          <input
            id="password"
            type="password"
            className="input"
            autoComplete="current-password"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            required
          />
        </div>
        <button className="btn-primary w-full" disabled={login.isPending}>
          {login.isPending ? 'Logging in…' : 'Log in'}
        </button>
        <Link to="/forgot-password" className="block text-center text-xs text-ink-400 hover:text-ink-100">
          Forgot your password?
        </Link>
      </form>
      <FormMessage error={login.isError ? errorMessage(login.error) : null} />
    </AuthCard>
  );
}
