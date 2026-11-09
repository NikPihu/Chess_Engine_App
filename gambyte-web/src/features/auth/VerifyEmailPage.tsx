import { useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '@/lib/api';
import { AuthCard, FormMessage } from './AuthCard';
import { meKey } from './useAuth';

/** Opened from the welcome email: confirms the address automatically. */
export default function VerifyEmailPage() {
  const [params] = useSearchParams();
  const qc = useQueryClient();
  const verify = useMutation({
    mutationFn: () => api.post('/api/auth/verify-email', { token: params.get('token') }),
    onSuccess: () => qc.invalidateQueries({ queryKey: meKey }),
  });
  const { mutate } = verify;

  useEffect(() => {
    mutate();
  }, [mutate]);

  return (
    <AuthCard title="Confirm email">
      {verify.isPending && <p className="text-sm text-ink-300">Checking your link…</p>}
      {verify.isSuccess && (
        <p className="text-sm">
          Email confirmed - thanks!{' '}
          <Link to="/" className="text-brand-400 hover:underline">
            Start playing
          </Link>
        </p>
      )}
      <FormMessage error={verify.isError ? errorMessage(verify.error) : null} />
    </AuthCard>
  );
}
