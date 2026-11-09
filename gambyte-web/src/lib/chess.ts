/**
 * Small chess helpers on top of chess.js. The browser uses chess.js only for instant feedback
 * (which squares a piece can go to, showing your move before the server answers); the server
 * checks every move again and is the only authority (ADR-0003).
 */
import { Chess, type Square } from 'chess.js';

/** A chess.js game rebuilt from a start position and UCI moves. */
export function replay(startFen: string, uciMoves: string[]): Chess {
  const chess = new Chess(startFen);
  for (const m of uciMoves) {
    chess.move({ from: m.slice(0, 2), to: m.slice(2, 4), promotion: m[4] });
  }
  return chess;
}

/** Squares the piece on `from` may move to. */
export function destinations(chess: Chess, from: string): string[] {
  return chess.moves({ square: from as Square, verbose: true }).map((m) => m.to);
}

/** True if moving from → to is a pawn reaching the last rank (needs a promotion piece). */
export function isPromotion(chess: Chess, from: string, to: string): boolean {
  return chess
    .moves({ square: from as Square, verbose: true })
    .some((m) => m.to === to && m.promotion !== undefined);
}

/** Tries a move; returns its UCI form, or null if illegal. */
export function tryMove(chess: Chess, from: string, to: string, promotion = 'q'): string | null {
  try {
    const m = chess.move({ from, to, promotion });
    return m.from + m.to + (m.promotion ?? '');
  } catch {
    return null;
  }
}

/** The square of the king in check, if any (for the red highlight). */
export function checkedKing(chess: Chess): string | null {
  if (!chess.inCheck()) return null;
  const turn = chess.turn();
  for (const row of chess.board()) {
    for (const sq of row) if (sq && sq.type === 'k' && sq.color === turn) return sq.square;
  }
  return null;
}

/** "3+2" style label for a time control (seconds). */
export function timeControlLabel(initialMs: number, incrementMs: number): string {
  if (initialMs === 0 && incrementMs === 0) return 'Unlimited';
  const min = initialMs / 60000;
  const minText = min >= 1 ? String(min) : min === 0.5 ? '½' : min === 0.25 ? '¼' : min.toFixed(2);
  return `${minText}+${incrementMs / 1000}`;
}

/** "mm:ss" or "s.t" (tenths) under 10 seconds, like real chess clocks. */
export function formatClock(ms: number): string {
  const clamped = Math.max(0, ms);
  if (clamped < 10_000) return (clamped / 1000).toFixed(1);
  const total = Math.ceil(clamped / 1000);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const mm = h > 0 ? String(m).padStart(2, '0') : String(m);
  return (h > 0 ? `${h}:` : '') + `${mm}:${String(s).padStart(2, '0')}`;
}
