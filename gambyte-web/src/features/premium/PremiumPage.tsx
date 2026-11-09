/**
 * Pricing and checkout. Payment runs in Razorpay's own secure window (Razorpay Checkout); card or
 * UPI details never touch Gambyte's servers. After payment the server verifies the signature and
 * turns premium on.
 */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, Crown } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { Spinner } from '@/components/Spinner';
import { meKey, useMe } from '@/features/auth/useAuth';

interface Plan {
  id: 'monthly' | 'yearly';
  name: string;
  priceInr: number;
  period: string;
  perks: string[];
}

interface Checkout {
  provider: string;
  keyId: string;
  subscriptionId: string;
}

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => { open: () => void };
  }
}

/** Loads Razorpay's checkout script only when someone actually wants to pay. */
function loadRazorpay(): Promise<void> {
  if (window.Razorpay) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = 'https://checkout.razorpay.com/v1/checkout.js';
    s.onload = () => resolve();
    s.onerror = () => reject(new Error('Could not load the payment window'));
    document.body.appendChild(s);
  });
}

export default function PremiumPage() {
  const { data: me } = useMe();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const plans = useQuery({
    queryKey: ['plans'],
    queryFn: () => api.get<{ plans: Plan[]; paymentsEnabled: boolean }>('/api/premium/plans'),
  });
  const cancel = useMutation({ mutationFn: () => api.post('/api/premium/cancel') });

  async function buy(plan: Plan) {
    if (!me) return navigate('/login?next=/premium');
    setError(null);
    try {
      const checkout = await api.post<Checkout>('/api/premium/subscribe', { plan: plan.id });
      await loadRazorpay();
      const rzp = new window.Razorpay!({
        key: checkout.keyId,
        subscription_id: checkout.subscriptionId,
        name: 'Gambyte',
        description: plan.name,
        prefill: { email: me.email },
        theme: { color: '#16a34a' },
        handler: async (r: { razorpay_payment_id: string; razorpay_subscription_id: string; razorpay_signature: string }) => {
          try {
            await api.post('/api/premium/confirm', {
              razorpayPaymentId: r.razorpay_payment_id,
              razorpaySubscriptionId: r.razorpay_subscription_id,
              razorpaySignature: r.razorpay_signature,
            });
            setSuccess(true);
            void qc.invalidateQueries({ queryKey: meKey });
          } catch (e) {
            setError(errorMessage(e));
          }
        },
      });
      rzp.open();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  if (plans.isLoading) return <Spinner />;

  return (
    <div className="mx-auto max-w-4xl">
      <header className="mb-8 text-center">
        <Crown className="mx-auto mb-2 text-amber-300" size={36} />
        <h1 className="text-3xl font-extrabold">Gambyte Premium</h1>
        <p className="mt-2 text-ink-400">Playing on Gambyte is free forever. Premium adds unlimited training tools.</p>
      </header>

      {me?.premium && (
        <div className="card mb-6 p-4 text-center">
          <p className="font-semibold text-amber-300">You are Premium until {new Date(me.premiumUntil!).toLocaleDateString()}. Thank you!</p>
          <button className="btn-ghost mt-2 text-sm" onClick={() => cancel.mutate()} disabled={cancel.isPending || cancel.isSuccess}>
            {cancel.isSuccess ? 'Renewal cancelled - premium stays until the date above' : 'Cancel renewal'}
          </button>
        </div>
      )}
      {success && <p className="card mb-6 p-4 text-center text-brand-400">Payment received - welcome to Premium!</p>}

      <div className="grid gap-4 md:grid-cols-2">
        {plans.data?.plans.map((p) => (
          <div key={p.id} className="card flex flex-col p-6">
            <h2 className="text-lg font-bold">{p.name}</h2>
            <p className="mt-2 text-4xl font-extrabold">
              ₹{p.priceInr}
              <span className="text-base font-normal text-ink-400"> / {p.period}</span>
            </p>
            {p.id === 'yearly' && <p className="text-sm text-brand-400">Save {Math.round(100 - (1499 / (199 * 12)) * 100)}%</p>}
            <ul className="mt-4 flex-1 space-y-2 text-sm">
              {p.perks.map((perk) => (
                <li key={perk} className="flex gap-2"><Check size={16} className="shrink-0 text-brand-400" /> {perk}</li>
              ))}
            </ul>
            <button className="btn-primary mt-6" disabled={!plans.data?.paymentsEnabled || me?.premium} onClick={() => buy(p)}>
              {plans.data?.paymentsEnabled ? (me?.premium ? 'Active' : 'Subscribe') : 'Coming soon'}
            </button>
          </div>
        ))}
      </div>
      {error && <p className="mt-4 text-center text-sm text-red-300">{error}</p>}
      <p className="mt-6 text-center text-xs text-ink-400">Payments by Razorpay (UPI, cards, netbanking). Cancel any time.</p>
    </div>
  );
}
