import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { errorMessage } from '@/lib/api';
import { AuthCard, FormMessage } from './AuthCard';
import { useRegister } from './useAuth';

/** Same rule as the server (UserDtos.USERNAME_REGEX), checked early for a friendlier form. */
const USERNAME = /^[A-Za-z0-9][A-Za-z0-9_-]{2,19}$/;

/** Create an account. The user is logged in right away; email confirmation can happen later. */
export default function RegisterPage() {
  const register = useRegister();
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: '', email: '', password: '' });
  const usernameBad = form.username.length > 0 && !USERNAME.test(form.username);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (usernameBad) return;
    register.mutate(form, { onSuccess: () => navigate('/') });
  };

  return (
    <AuthCard
      title="Create your account"
      footer={
        <>
          Already have one?{' '}
          <Link to="/login" className="text-noir-50 underline decoration-ember-500 underline-offset-4">
            Log in
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-4">
        <div>
          <label className="label" htmlFor="username">
            Username
          </label>
          <input
            id="username"
            className="input"
            autoComplete="username"
            value={form.username}
            onChange={(e) => setForm({ ...form, username: e.target.value })}
            required
          />
          {usernameBad && (
            <p className="mt-1 text-xs text-loss">3-20 letters, digits, _ or -, starting with a letter or digit</p>
          )}
        </div>
        <div>
          <label className="label" htmlFor="email">
            Email
          </label>
          <input
            id="email"
            type="email"
            className="input"
            autoComplete="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
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
            autoComplete="new-password"
            minLength={8}
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            required
          />
          <p className="mt-1 text-xs text-ink-400">At least 8 characters.</p>
        </div>
        <button className="btn-primary w-full py-3" disabled={register.isPending}>
          {register.isPending ? 'Creating…' : 'Sign up'}
        </button>
      </form>
      <FormMessage error={register.isError ? errorMessage(register.error) : null} />
    </AuthCard>
  );
}
