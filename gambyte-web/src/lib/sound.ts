/**
 * Move sounds, synthesised with the Web Audio API (no audio files to download).
 * A short low "tock" for moves, a sharper double click for captures, a chime for game start/end.
 */
let ctx: AudioContext | null = null;
let enabled = true;

export function setSoundEnabled(on: boolean) {
  enabled = on;
}

function tone(freq: number, duration: number, gain: number, type: OscillatorType = 'sine', delay = 0) {
  if (!enabled) return;
  ctx ??= new AudioContext();
  const t = ctx.currentTime + delay;
  const osc = ctx.createOscillator();
  const g = ctx.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, t);
  g.gain.setValueAtTime(gain, t);
  g.gain.exponentialRampToValueAtTime(0.0001, t + duration);
  osc.connect(g).connect(ctx.destination);
  osc.start(t);
  osc.stop(t + duration);
}

export const sound = {
  move: () => tone(220, 0.08, 0.25, 'triangle'),
  capture: () => {
    tone(320, 0.06, 0.3, 'square');
    tone(180, 0.08, 0.25, 'triangle', 0.04);
  },
  check: () => tone(660, 0.12, 0.2, 'sine'),
  start: () => {
    tone(523, 0.12, 0.15);
    tone(784, 0.18, 0.15, 'sine', 0.1);
  },
  end: () => {
    tone(784, 0.15, 0.15);
    tone(523, 0.25, 0.15, 'sine', 0.12);
  },
  lowTime: () => tone(880, 0.05, 0.1, 'square'),
};

/** Picks the right sound for a SAN move ("exd5", "Qh5+"). */
export function playMoveSound(san: string | undefined) {
  if (!san) return sound.move();
  if (san.includes('+') || san.includes('#')) return sound.check();
  if (san.includes('x')) return sound.capture();
  return sound.move();
}
