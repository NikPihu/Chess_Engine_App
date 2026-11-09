import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { api, errorMessage } from '@/lib/api';
import { AuthCard, FormMessage } from './AuthCard';

/** Opened from the reset email: choose a new password. */
export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const [password, setPassword] = useState('');
  const reset = useMutation({
    mutationFn: () => api.post('/api/auth/reset-password', { token: params.get('token'), password }),
  });

  return (
    <AuthCard title="Choose a new password">
      {reset.isSuccess ? (
        <p className="text-sm">
          Password changed.{' '}
          <Link to="/login" className="text-ember-300 hover:underline">
            Log in
          </Link>
        </p>
      ) : (
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            reset.mutate();
          }}
        >
          <input
            type="password"
            className="input"
            placeholder="New password (8+ characters)"
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <button className="btn-primary w-full" disabled={reset.isPending}>
            Save password
          </button>
        </form>
      )}
      <FormMessage error={reset.isError ? errorMessage(reset.error) : null} />
    </AuthCard>
  );
}
