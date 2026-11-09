/**
 * Plays one puzzle on the board: shows the setup move (if any), lets the solver move, checks each
 * move against the solution, plays the opponent's replies automatically, and reports the result.
 *
 * A move is accepted if it equals the solution move OR it gives checkmate (puzzles can have
 * several mates; both are right).
 */
import { useEffect, useState } from 'react';
import { Chess } from 'chess.js';
import { Board } from '@/features/game/Board';
import { useSettings } from '@/features/profile/useSettings';
import { playMoveSound, sound } from '@/lib/sound';
import type { Color } from '@/lib/types';

export interface PuzzleData {
  id: string;
  fen: string;
  setupMove?: string;
  solution: string[];
  rating: number;
  plays: number;
  themes: string[];
}

export type PuzzleOutcome = 'solved' | 'failed';

export function PuzzleBoard({
  puzzle,
  onDone,
  showHint,
}: {
  puzzle: PuzzleData;
  onDone: (outcome: PuzzleOutcome) => void;
  showHint?: boolean;
}) {
  const settings = useSettings();
  // The stored FEN is the position the solver faces (after the setup move, if any).
  const startFen = puzzle.fen;
  const solverColor: Color = new Chess(startFen).turn() === 'w' ? 'white' : 'black';
  const [fen, setFen] = useState(startFen);
  const [step, setStep] = useState(0); // index into solution
  const [state, setState] = useState<'playing' | 'wrong' | 'solved'>('playing');
  const [lastMove, setLastMove] = useState<string | undefined>(puzzle.setupMove);
  const [answer, setAnswer] = useState<string | null>(null);

  useEffect(() => {
    setFen(startFen);
    setStep(0);
    setState('playing');
    setLastMove(puzzle.setupMove);
    setAnswer(null);
  }, [puzzle, startFen]);

  function onMove(uci: string): boolean {
    if (state !== 'playing') return false;
    const chess = new Chess(fen);
    let san: string;
    try {
      san = chess.move({ from: uci.slice(0, 2), to: uci.slice(2, 4), promotion: uci[4] ?? 'q' }).san;
    } catch {
      return false;
    }
    const expected = puzzle.solution[step];
    const correct = uci === expected || (expected && uci.slice(0, 4) === expected.slice(0, 4) && !expected[4]) || chess.isCheckmate();
    setFen(chess.fen());
    setLastMove(uci);
    playMoveSound(san);
    if (!correct) {
      try {
        const e = puzzle.solution[step];
        setAnswer(new Chess(fen).move({ from: e.slice(0, 2), to: e.slice(2, 4), promotion: e[4] }).san);
      } catch {
        setAnswer(null);
      }
      setState('wrong');
      sound.lowTime();
      onDone('failed');
      return true;
    }
    const next = step + 1;
    if (next >= puzzle.solution.length || chess.isCheckmate()) {
      setState('solved');
      sound.end();
      onDone('solved');
      return true;
    }
    // Opponent's reply from the solution, after a short pause so it can be seen.
    const reply = puzzle.solution[next];
    setTimeout(() => {
      const c2 = new Chess(chess.fen());
      const m = c2.move({ from: reply.slice(0, 2), to: reply.slice(2, 4), promotion: reply[4] });
      playMoveSound(m.san);
      setFen(c2.fen());
      setLastMove(reply);
      setStep(next + 1);
    }, 350);
    return true;
  }

  const hintSquare = showHint && state === 'playing' ? puzzle.solution[step]?.slice(0, 2) : undefined;

  return (
    <div className="relative">
      <Board
        fen={fen}
        orientation={solverColor}
        movable={state === 'playing' ? solverColor : null}
        onMove={onMove}
        lastMove={lastMove}
        theme={settings.boardTheme}
        autoQueen={settings.autoQueen}
        premoves={false}
        showCoordinates={settings.showCoordinates}
        arrows={hintSquare ? [{ startSquare: hintSquare, endSquare: puzzle.solution[step].slice(2, 4), color: 'rgba(34,197,94,.7)' }] : []}
      />
      <p className="mt-2 text-center text-sm text-ink-300">
        {state === 'playing' && `${solverColor === 'white' ? 'White' : 'Black'} to move - find the best move`}
        {state === 'solved' && <span className="font-semibold text-brand-400">Solved!</span>}
        {state === 'wrong' && <span className="font-semibold text-red-300">Not the best move.{answer && ` The answer was ${answer}.`}</span>}
      </p>
    </div>
  );
}
