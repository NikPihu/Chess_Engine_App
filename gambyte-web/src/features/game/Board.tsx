/**
 * The interactive board used everywhere (games, puzzles, lessons, analysis).
 *
 * Built on react-chessboard (MIT). Adds what a playing site needs:
 * - drag-and-drop AND click-to-move (tap a piece, tap a square: better on phones)
 * - legal-move dots, last-move and check highlights
 * - promotion picker (or auto-queen)
 * - premoves: a move made while it's the opponent's turn is queued and played the instant your
 *   turn starts (shown in blue)
 *
 * The board is "dumb" about game rules: the parent passes `fen` and `onMove`, and decides whether
 * the move is accepted. chess.js here only computes legal squares for the highlights.
 */
import { useEffect, useMemo, useState } from 'react';
import { Chessboard } from 'react-chessboard';
import type { Arrow } from 'react-chessboard';
import { Chess } from 'chess.js';
import { checkedKing, destinations, isPromotion } from '@/lib/chess';
import type { Color } from '@/lib/types';

export const BOARD_THEMES = {
  noir: { light: '#d8d2c8', dark: '#5e554d' },
  ember: { light: '#efe3d4', dark: '#b2694b' },
  green: { light: '#eeeed2', dark: '#769656' },
  brown: { light: '#f0d9b5', dark: '#b58863' },
  blue: { light: '#dee3e6', dark: '#8ca2ad' },
  gray: { light: '#d9d9d9', dark: '#8b8b8b' },
} as const;

export type BoardTheme = keyof typeof BOARD_THEMES;

export interface BoardProps {
  fen: string;
  orientation: Color;
  /** Which colour the user may move; null = view only. */
  movable: Color | null;
  /** Called with a UCI move; return false to snap the piece back. */
  onMove: (uci: string) => boolean;
  lastMove?: string;
  arrows?: Arrow[];
  theme?: BoardTheme;
  premoves?: boolean;
  autoQueen?: boolean;
  showCoordinates?: boolean;
}

export function Board({
  fen,
  orientation,
  movable,
  onMove,
  lastMove,
  arrows = [],
  theme = 'noir',
  premoves = true,
  autoQueen = false,
  showCoordinates = true,
}: BoardProps) {
  const chess = useMemo(() => new Chess(fen), [fen]);
  const [selected, setSelected] = useState<string | null>(null);
  const [premove, setPremove] = useState<{ from: string; to: string } | null>(null);
  const [promotion, setPromotion] = useState<{ from: string; to: string } | null>(null);

  const myTurn = movable !== null && chess.turn() === movable[0];

  // A new position: clear selection; if it is now our turn and a premove is queued, play it.
  useEffect(() => {
    setSelected(null);
    if (premove && myTurn) {
      const legal = destinations(chess, premove.from).includes(premove.to);
      setPremove(null);
      if (legal) submit(premove.from, premove.to);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fen]);

  function submit(from: string, to: string, promo?: string) {
    if (!promo && isPromotion(chess, from, to)) {
      if (autoQueen) promo = 'q';
      else {
        setPromotion({ from, to });
        return true;
      }
    }
    return onMove(from + to + (promo ?? ''));
  }

  function attempt(from: string, to: string): boolean {
    if (movable === null || from === to) return false;
    if (!myTurn) {
      // Not our turn: remember it as a premove (any own piece to any square; checked later).
      const piece = chess.get(from as never);
      if (premoves && piece && piece.color === movable[0]) setPremove({ from, to });
      return false;
    }
    if (!destinations(chess, from).includes(to)) return false;
    return submit(from, to);
  }

  function onSquareClick(square: string) {
    if (movable === null) return;
    const piece = chess.get(square as never);
    if (selected) {
      if (selected === square) return setSelected(null);
      if (piece && piece.color === movable[0]) return setSelected(square);
      attempt(selected, square);
      return setSelected(null);
    }
    if (piece && piece.color === movable[0]) setSelected(square);
    else setPremove(null);
  }

  // Square highlights: last move (yellow), selection, legal targets (dots), check (red), premove (blue).
  const squareStyles: Record<string, React.CSSProperties> = {};
  if (lastMove) {
    squareStyles[lastMove.slice(0, 2)] = { background: 'rgba(229, 83, 45, 0.38)' };
    squareStyles[lastMove.slice(2, 4)] = { background: 'rgba(229, 83, 45, 0.5)' };
  }
  const king = checkedKing(chess);
  if (king) squareStyles[king] = { background: 'radial-gradient(circle, rgba(239,68,68,.9) 0%, rgba(239,68,68,.3) 60%, transparent 75%)' };
  if (selected) {
    squareStyles[selected] = { background: 'rgba(255, 255, 255, 0.28)' };
    if (myTurn) {
      for (const sq of destinations(chess, selected)) {
        const occupied = chess.get(sq as never);
        squareStyles[sq] = occupied
          ? { background: 'radial-gradient(circle, transparent 58%, rgba(10,10,12,.32) 60%)' }
          : { background: 'radial-gradient(circle, rgba(10,10,12,.3) 20%, transparent 24%)' };
      }
    }
  }
  if (premove) {
    squareStyles[premove.from] = { background: 'rgba(59, 130, 246, 0.5)' };
    squareStyles[premove.to] = { background: 'rgba(59, 130, 246, 0.6)' };
  }

  const colors = BOARD_THEMES[theme];

  return (
    <div className="relative aspect-square w-full select-none">
      <Chessboard
        options={{
          position: fen,
          boardOrientation: orientation,
          allowDragging: movable !== null,
          canDragPiece: ({ piece }) => movable !== null && piece.pieceType[0] === movable[0],
          onPieceDrop: ({ sourceSquare, targetSquare }) =>
            targetSquare ? attempt(sourceSquare, targetSquare) : false,
          onSquareClick: ({ square }) => onSquareClick(square),
          onSquareRightClick: () => {
            setPremove(null);
            setSelected(null);
          },
          squareStyles,
          arrows,
          showNotation: showCoordinates,
          animationDurationInMs: 150,
          lightSquareStyle: { backgroundColor: colors.light },
          darkSquareStyle: { backgroundColor: colors.dark },
          boardStyle: { borderRadius: '14px', overflow: 'hidden', boxShadow: '0 40px 80px -30px rgba(0,0,0,.9), 0 0 0 1px rgba(255,255,255,.06)' },
          darkSquareNotationStyle: { color: '#d8d2c8', fontFamily: 'JetBrains Mono Variable, monospace', fontSize: '10px', opacity: 0.8 },
          lightSquareNotationStyle: { color: '#5e554d', fontFamily: 'JetBrains Mono Variable, monospace', fontSize: '10px', opacity: 0.8 },
        }}
      />
      {promotion && (
        <PromotionPicker
          color={movable ?? 'white'}
          onPick={(p) => {
            const { from, to } = promotion;
            setPromotion(null);
            if (p) onMove(from + to + p);
          }}
        />
      )}
    </div>
  );
}

function PromotionPicker({ color, onPick }: { color: Color; onPick: (piece: string | null) => void }) {
  const pieces = ['q', 'r', 'b', 'n'];
  const glyph: Record<string, string> = color === 'white'
    ? { q: '♕', r: '♖', b: '♗', n: '♘' }
    : { q: '♛', r: '♜', b: '♝', n: '♞' };
  return (
    <div
      className="absolute inset-0 z-20 flex items-center justify-center rounded-[14px] bg-black/60 backdrop-blur-sm"
      onClick={() => onPick(null)}
    >
      <div className="card flex gap-2 p-3" onClick={(e) => e.stopPropagation()}>
        {pieces.map((p) => (
          <button
            key={p}
            className="flex h-16 w-16 items-center justify-center rounded-2xl bg-white/5 text-5xl transition hover:bg-ember-500"
            onClick={() => onPick(p)}
            aria-label={`Promote to ${p}`}
          >
            {glyph[p]}
          </button>
        ))}
      </div>
    </div>
  );
}
