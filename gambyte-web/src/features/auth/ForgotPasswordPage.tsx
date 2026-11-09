import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { api, errorMessage } from '@/lib/api';
import { AuthCard, FormMessage } from './AuthCard';

/** Ask for a password-reset email. The answer is the same whether or not the email exists. */
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const send = useMutation({ mutationFn: () => api.post('/api/auth/forgot-password', { email }) });

  return (
    <AuthCard title="Reset your password">
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault();
          send.mutate();
        }}
      >
        <div>
          <label className="label" htmlFor="email">
            Email
          </label>
          <input id="email" type="email" className="input" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </div>
        <button className="btn-primary w-full" disabled={send.isPending}>
          Send reset link
        </button>
      </form>
      <FormMessage
        error={send.isError ? errorMessage(send.error) : null}
        success={send.isSuccess ? 'If that email has an account, a reset link is on its way.' : null}
      />
    </AuthCard>
  );
}
