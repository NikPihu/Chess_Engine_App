/**
 * One shared STOMP-over-WebSocket connection for the whole app (live games, lobby, notifications).
 *
 * Components call {@link subscribe}; the connection is opened on first use, reconnects by itself
 * (with back-off) after network drops, and re-subscribes everything after a reconnect. Each
 * subscriber gets an `onReconnect` hook so it can re-fetch a full snapshot of what it shows,
 * because messages sent while offline are lost.
 *
 * Lag: the client measures round-trip time with a ping and reports half of it with each move;
 * the server gives that time back on the clock (capped, see ChessClock.java).
 */
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';

type Handler = (body: unknown) => void;

interface Entry {
  destination: string;
  handler: Handler;
  onReconnect?: () => void;
  sub?: StompSubscription;
}

const entries = new Set<Entry>();
let client: Client | null = null;
let connectedOnce = false;
let lagMs = 50;
const statusListeners = new Set<(connected: boolean) => void>();

function wsUrl(): string {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  return `${scheme}://${location.host}/ws`;
}

function ensureClient(): Client {
  if (client) return client;
  client = new Client({
    brokerURL: wsUrl(),
    reconnectDelay: 1000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      for (const e of entries) attach(e);
      if (connectedOnce) for (const e of entries) e.onReconnect?.();
      connectedOnce = true;
      statusListeners.forEach((l) => l(true));
    },
    onWebSocketClose: () => statusListeners.forEach((l) => l(false)),
  });
  client.activate();
  // Presence heartbeat + lag measurement every 20 s.
  setInterval(() => {
    if (!client?.connected) return;
    const sent = performance.now();
    // The STOMP broker confirms a frame that asks for a receipt; half the round trip ≈ lag.
    const receipt = `ping-${sent}`;
    client.watchForReceipt(receipt, () => {
      lagMs = Math.min(500, (performance.now() - sent) / 2);
    });
    client.publish({ destination: '/app/ping', body: '{}', headers: { receipt } });
  }, 20000);
  return client;
}

function attach(e: Entry) {
  if (!client?.connected) return;
  e.sub = client.subscribe(e.destination, (msg: IMessage) => {
    try {
      e.handler(JSON.parse(msg.body));
    } catch {
      e.handler(msg.body);
    }
  });
}

/** Listens to a destination; returns a function that stops listening. */
export function subscribe(destination: string, handler: Handler, onReconnect?: () => void): () => void {
  const entry: Entry = { destination, handler, onReconnect };
  entries.add(entry);
  const c = ensureClient();
  if (c.connected) attach(entry);
  return () => {
    entry.sub?.unsubscribe();
    entries.delete(entry);
  };
}

/** Sends a message to the server (e.g. a move); returns false if not connected right now. */
export function send(destination: string, body: unknown): boolean {
  const c = ensureClient();
  if (!c.connected) return false;
  c.publish({ destination, body: JSON.stringify(body) });
  return true;
}

export function currentLagMs(): number {
  return Math.round(lagMs);
}

export function onConnectionChange(listener: (connected: boolean) => void): () => void {
  statusListeners.add(listener);
  return () => statusListeners.delete(listener);
}

/** After login/logout the socket must reconnect so the server sees the new session. */
export function reconnect() {
  if (!client) return;
  void client.deactivate().then(() => client?.activate());
}
