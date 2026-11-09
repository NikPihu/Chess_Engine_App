/**
 * Pricing and checkout. Payment runs in Razorpay's own secure window (Razorpay Checkout); card or
 * UPI details never touch Gambyte's servers. After payment the server verifies the signature and
 * turns premium on.
 */
import { useState } from 'react';
import clsx from 'clsx';
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
    <div className="relative mx-auto max-w-5xl">
      <div className="pointer-events-none absolute -top-20 left-1/2 h-80 w-[60%] -translate-x-1/2 rounded-full bg-gold/10 blur-[120px]" />
      <header className="relative mb-12 animate-rise text-center">
        <span className="tag"><Crown size={13} className="text-gold" /> Gambyte Premium</span>
        <h1 className="display mt-6 text-[clamp(3rem,8vw,6.5rem)]">
          Train <span className="font-extralight text-noir-400">without</span>
          <br />
          limits<span className="text-gold">.</span>
        </h1>
        <p className="mx-auto mt-6 max-w-lg text-noir-400">Playing on Gambyte is free forever. Premium unlocks unlimited reviews, the AI coach and unlimited puzzles.</p>
      </header>

      {me?.premium && (
        <div className="card mb-6 flex flex-wrap items-center justify-between gap-3 border-gold/30 p-5">
          <p className="flex items-center gap-2 text-gold"><Crown size={16} /> Premium until {new Date(me.premiumUntil!).toLocaleDateString()}. Thank you!</p>
          <button className="btn-ghost text-sm" onClick={() => cancel.mutate()} disabled={cancel.isPending || cancel.isSuccess}>
            {cancel.isSuccess ? 'Renewal cancelled - premium stays until then' : 'Cancel renewal'}
          </button>
        </div>
      )}
      {success && <p className="card mb-6 border-win/30 p-5 text-center text-win">Payment received - welcome to Premium!</p>}

      <div className="relative grid gap-5 md:grid-cols-2">
        {plans.data?.plans.map((p) => {
          const best = p.id === 'yearly';
          return (
            <div key={p.id} className={clsx('card relative flex flex-col overflow-hidden p-8', best && 'border-gold/40')}>
              {best && <div className="absolute -top-24 -right-16 h-64 w-64 rounded-full bg-gold/15 blur-3xl" />}
              <div className="relative flex items-center justify-between">
                <p className="eyebrow">{p.id === 'monthly' ? '01/ Monthly' : '02/ Yearly'}</p>
                {best && <span className="rounded-full bg-gold px-3 py-1 font-mono text-[10px] font-bold tracking-widest text-noir-950">SAVE {Math.round(100 - (1499 / (199 * 12)) * 100)}%</span>}
              </div>
              <p className="relative mt-8 flex items-baseline gap-2">
                <span className="num text-6xl font-extralight tracking-tight">₹{p.priceInr}</span>
                <span className="text-noir-400">/ {p.period}</span>
              </p>
              <ul className="relative mt-8 flex-1 space-y-3 text-sm">
                {p.perks.map((perk) => (
                  <li key={perk} className="flex gap-3 text-noir-200"><Check size={16} className="mt-0.5 shrink-0 text-gold" /> {perk}</li>
                ))}
              </ul>
              <button className={clsx('relative mt-8 py-3.5', best ? 'btn-primary' : 'btn-secondary')} disabled={!plans.data?.paymentsEnabled || me?.premium} onClick={() => buy(p)}>
                {plans.data?.paymentsEnabled ? (me?.premium ? 'Active' : 'Subscribe') : 'Coming soon'}
              </button>
            </div>
          );
        })}
      </div>
      {error && <p className="mt-4 text-center text-sm text-loss">{error}</p>}
      <p className="eyebrow mt-8 text-center">Payments by Razorpay · UPI, cards, netbanking · cancel any time</p>
    </div>
  );
}
