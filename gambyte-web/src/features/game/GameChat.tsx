/** In-game chat between the two players (filtered on the server). */
import { useEffect, useRef, useState } from 'react';
import { Send } from 'lucide-react';
import type { ChatMessage } from './useLiveGame';

export function GameChat({ messages, onSend }: { messages: ChatMessage[]; onSend: (text: string) => void }) {
  const [text, setText] = useState('');
  const end = useRef<HTMLDivElement>(null);
  useEffect(() => end.current?.scrollIntoView({ block: 'nearest' }), [messages]);

  return (
    <div className="card flex flex-col p-3">
      <h2 className="eyebrow mb-3">Chat</h2>
      <div className="h-32 overflow-y-auto text-sm">
        {messages.length === 0 && <p className="text-ink-400">Say hi - be nice!</p>}
        {messages.map((m, i) => (
          <p key={i}>
            <span className="font-semibold text-ink-300">{m.by}: </span>
            {m.text}
          </p>
        ))}
        <div ref={end} />
      </div>
      <form
        className="mt-2 flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (!text.trim()) return;
          onSend(text.trim());
          setText('');
        }}
      >
        <input className="input" maxLength={140} value={text} onChange={(e) => setText(e.target.value)} placeholder="Message" />
        <button className="btn-secondary px-3" aria-label="Send">
          <Send size={16} />
        </button>
      </form>
    </div>
  );
}
