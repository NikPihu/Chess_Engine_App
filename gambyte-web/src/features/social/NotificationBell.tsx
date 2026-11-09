/** The bell in the header: unread count, a dropdown of recent notifications, live updates. */
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell } from 'lucide-react';
import { api } from '@/lib/api';
import { subscribe } from '@/lib/ws';
import { useMe } from '@/features/auth/useAuth';

interface Notification {
  id: number;
  kind: string;
  text: string;
  link?: string;
  read: boolean;
  createdAt: string;
}

export function NotificationBell() {
  const { data: me } = useMe();
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const { data } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => api.get<{ unread: number; items: Notification[] }>('/api/notifications'),
    enabled: Boolean(me),
  });

  useEffect(() => {
    if (!me) return;
    return subscribe('/user/queue/notify', () => void qc.invalidateQueries({ queryKey: ['notifications'] }));
  }, [me, qc]);

  useEffect(() => {
    const close = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  if (!me) return null;
  const unread = data?.unread ?? 0;

  return (
    <div className="relative" ref={ref}>
      <button
        className="btn-ghost relative px-2"
        aria-label={`Notifications (${unread} unread)`}
        onClick={() => {
          setOpen((o) => !o);
          if (unread > 0) void api.post('/api/notifications/read').then(() => qc.invalidateQueries({ queryKey: ['notifications'] }));
        }}
      >
        <Bell size={18} />
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-600 px-1 text-[10px] font-bold text-white">
            {unread}
          </span>
        )}
      </button>
      {open && (
        <div className="card absolute right-0 z-40 mt-2 w-80 overflow-hidden">
          <p className="border-b border-ink-700 px-4 py-2 text-sm font-semibold">Notifications</p>
          <div className="max-h-96 overflow-y-auto">
            {data?.items.length === 0 && <p className="p-4 text-sm text-ink-400">Nothing yet.</p>}
            {data?.items.map((n) => (
              <Link key={n.id} to={n.link ?? '#'} onClick={() => setOpen(false)} className="block border-b border-ink-800 px-4 py-2.5 text-sm hover:bg-ink-800">
                <p className={n.read ? 'text-ink-300' : 'text-ink-100'}>{n.text}</p>
                <p className="text-xs text-ink-400">{new Date(n.createdAt).toLocaleString()}</p>
              </Link>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
