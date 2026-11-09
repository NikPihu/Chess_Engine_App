/**
 * Analysis board: move pieces freely (both colours), load a FEN or a finished game, and see the
 * engine's evaluation, best move (as an arrow) and main line for every position.
 */
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Chess } from 'chess.js';
import { RotateCcw, FlipVertical2 } from 'lucide-react';
import { api } from '@/lib/api';
import type { GameView } from '@/lib/types';
import { Board } from '@/features/game/Board';
import { MoveList } from '@/features/game/MoveList';
import { useSettings } from '@/features/profile/useSettings';
import { EvalBar } from './EvalBar';

interface Eval {
  bestMove: string;
  bestSan?: string;
  whiteCp: number;
  mate?: number;
  depth: number;
  pvSan: string[];
}

const START = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

export default function AnalysisPage() {
  const settings = useSettings();
  const [params] = useSearchParams();
  const [startFen, setStartFen] = useState(params.get('fen') ?? START);
  const [moves, setMoves] = useState<string[]>([]);
  const [ply, setPly] = useState<number | null>(null);
  const [flipped, setFlipped] = useState(false);
  const [fenInput, setFenInput] = useState('');

  // Load a finished game when opened as /analysis?game=ID
  const gameId = params.get('game');
  useEffect(() => {
    if (!gameId) return;
    void api.get<GameView>(`/api/games/${gameId}`).then((g) => {
      setStartFen(g.startFen);
      setMoves(g.moves);
      setPly(null);
    });
  }, [gameId]);

  const { fen, sans } = useMemo(() => {
    const chess = new Chess(startFen);
    const sans: string[] = [];
    const upto = ply === null ? moves.length : ply + 1;
    let fen = startFen;
    for (let i = 0; i < moves.length; i++) {
      const m = chess.move({ from: moves[i].slice(0, 2), to: moves[i].slice(2, 4), promotion: moves[i][4] });
      sans.push(m.san);
      if (i + 1 === upto) fen = chess.fen();
    }
    if (upto === 0) fen = startFen;
    return { fen, sans };
  }, [startFen, moves, ply]);

  const evaluation = useQuery({
    queryKey: ['eval', fen],
    queryFn: () => api.get<Eval>(`/api/analysis/eval?fen=${encodeURIComponent(fen)}`),
    staleTime: Infinity,
  });

  function onMove(uci: string) {
    const cut = ply === null ? moves.length : ply + 1;
    setMoves([...moves.slice(0, cut), uci]); // a new move from an earlier position starts a new line
    setPly(null);
    return true;
  }

  const turn = new Chess(fen).turn() === 'w' ? 'white' : 'black';
  const e = evaluation.data;

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
      <section className="mx-auto flex w-full max-w-[min(96vw,calc(100vh-10rem))] gap-2">
        <div className="shrink-0">
          <EvalBar cp={e?.whiteCp ?? 0} mate={e?.mate} flipped={flipped} />
        </div>
        <div className="min-w-0 flex-1">
          <Board
            fen={fen}
            orientation={flipped ? 'black' : 'white'}
            movable={turn}
            onMove={onMove}
            lastMove={ply === null ? moves[moves.length - 1] : moves[ply]}
            arrows={e?.bestMove && e.bestMove.length >= 4 ? [{ startSquare: e.bestMove.slice(0, 2), endSquare: e.bestMove.slice(2, 4), color: 'rgba(59,130,246,.75)' }] : []}
            theme={settings.boardTheme}
            premoves={false}
          />
        </div>
      </section>
      <aside className="space-y-4">
        <div className="card p-4">
          <h2 className="text-sm font-semibold text-ink-400">Gambyte Engine</h2>
          {evaluation.isFetching && <p className="text-sm text-ink-400">Thinking…</p>}
          {e && (
            <>
              <p className="text-2xl font-bold">
                {e.mate != null ? `Mate in ${Math.abs(e.mate)}` : `${e.whiteCp >= 0 ? '+' : ''}${(e.whiteCp / 100).toFixed(2)}`}
              </p>
              <p className="mt-1 text-sm text-ink-300">Best: <span className="font-mono">{e.bestSan ?? '-'}</span> · depth {e.depth}</p>
              <p className="mt-2 font-mono text-xs text-ink-400">{e.pvSan.slice(0, 10).join(' ')}</p>
            </>
          )}
        </div>
        <div className="card p-3">
          <MoveList sans={sans} current={ply ?? sans.length - 1} onSelect={setPly} />
        </div>
        <div className="card space-y-2 p-3">
          <div className="flex gap-2">
            <input className="input font-mono text-xs" placeholder="Paste a FEN" value={fenInput} onChange={(ev) => setFenInput(ev.target.value)} />
            <button
              className="btn-secondary"
              onClick={() => {
                try {
                  new Chess(fenInput.trim());
                  setStartFen(fenInput.trim());
                  setMoves([]);
                  setPly(null);
                } catch {
                  alert('That FEN is not valid');
                }
              }}
            >
              Load
            </button>
          </div>
          <div className="flex gap-2">
            <button className="btn-ghost" onClick={() => { setStartFen(START); setMoves([]); setPly(null); }}>
              <RotateCcw size={16} /> Reset
            </button>
            <button className="btn-ghost" onClick={() => setFlipped((f) => !f)}>
              <FlipVertical2 size={16} /> Flip
            </button>
          </div>
        </div>
      </aside>
    </div>
  );
}
