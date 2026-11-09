/** Report a player to the moderators. */
import { useState } from 'react';
import { X } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';

export function ReportDialog({ username, onClose }: { username: string; onClose: () => void }) {
  const [reason, setReason] = useState('cheat');
  const [text, setText] = useState('');
  const [gameId, setGameId] = useState('');
  const [status, setStatus] = useState<'idle' | 'sent' | string>('idle');

  async function send() {
    try {
      await api.post(`/api/users/${username}/report`, { reason, text, gameId: gameId.trim() || null });
      setStatus('sent');
    } catch (e) {
      setStatus(errorMessage(e));
    }
  }

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/60 p-4" onClick={onClose}>
      <div className="card w-full max-w-md space-y-3 p-5" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold">Report {username}</h2>
          <button className="btn-ghost px-2" onClick={onClose} aria-label="Close"><X size={18} /></button>
        </div>
        {status === 'sent' ? (
          <p className="text-sm text-brand-400">Thank you. A moderator will look at it.</p>
        ) : (
          <>
            <select className="input" value={reason} onChange={(e) => setReason(e.target.value)}>
              <option value="cheat">Cheating (engine use)</option>
              <option value="abuse">Insults or harassment</option>
              <option value="other">Something else</option>
            </select>
            <input className="input" placeholder="Game id (optional)" value={gameId} onChange={(e) => setGameId(e.target.value)} />
            <textarea className="input" rows={4} maxLength={1000} placeholder="What happened?" value={text} onChange={(e) => setText(e.target.value)} />
            <button className="btn-primary w-full" onClick={send}>Send report</button>
            {status !== 'idle' && <p className="text-sm text-red-300">{status}</p>}
          </>
        )}
      </div>
    </div>
  );
}
