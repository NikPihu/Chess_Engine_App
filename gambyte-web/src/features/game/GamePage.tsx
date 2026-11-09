/**
 * The game screen: board, both players with clocks, the move list, game actions, chat, and the
 * result when it ends. Works for players and spectators alike (spectators just can't move).
 */
import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  Flag,
  Handshake,
  RotateCcw,
  Undo2,
  X,
  Download,
  Search,
  Repeat,
  FlipVertical2,
  Sparkles,
} from 'lucide-react';
import { Spinner } from '@/components/Spinner';
import { useMe } from '@/features/auth/useAuth';
import { useSettings } from '@/features/profile/useSettings';
import { replay, timeControlLabel } from '@/lib/chess';
import type { Color, GameView } from '@/lib/types';
import { send } from '@/lib/ws';
import { Board } from './Board';
import { MoveList } from './MoveList';
import { PlayerBar } from './PlayerBar';
import { GameChat } from './GameChat';
import { useLiveGame } from './useLiveGame';

export default function GamePage() {
  const { id = '' } = useParams();
  const { data: me } = useMe();
  const settings = useSettings();
  const navigate = useNavigate();
  const { game, error, chat, rematchId, move, action, say } = useLiveGame(id);
  const [shownPly, setShownPly] = useState<number | null>(null); // null = live position
  const [flipped, setFlipped] = useState(false);
  const [confirmResign, setConfirmResign] = useState(false);

  // Follow a rematch automatically.
  useEffect(() => {
    if (rematchId) navigate(`/game/${rematchId}`);
  }, [rematchId, navigate]);

  // Tell the server when the tab loses focus during a game (fair-play statistics).
  useEffect(() => {
    const onBlur = () => {
      if (game?.status === 'STARTED') send(`/app/game/${id}/blur`, {});
    };
    window.addEventListener('blur', onBlur);
    return () => window.removeEventListener('blur', onBlur);
  }, [id, game?.status]);

  const myColor: Color | null = useMemo(() => {
    if (!game || !me) return null;
    if (game.white.id === me.id) return 'white';
    if (game.black.id === me.id) return 'black';
    return null;
  }, [game, me]);

  const shownFen = useMemo(() => {
    if (!game || shownPly === null) return game?.fen;
    return replay(game.startFen, game.moves.slice(0, shownPly + 1)).fen();
  }, [game, shownPly]);

  if (error && !game) return <p className="text-center text-ink-300">{error}</p>;
  if (!game || !shownFen) return <Spinner />;

  const live = game.status === 'STARTED';
  const orientation: Color = flipped ? opposite(myColor ?? 'white') : (myColor ?? 'white');
  const top = orientation === 'white' ? 'black' : 'white';
  const bottom = opposite(top);
  const lastMove = shownPly === null ? game.moves[game.moves.length - 1] : game.moves[shownPly];
  const vsEngine = Boolean(game.white.engineLevel || game.black.engineLevel);

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
      <section className="mx-auto w-full max-w-[min(92vw,calc(100vh-12rem))]">
        <PlayerBar
          player={game[top]}
          color={top}
          clock={game.clock}
          running={live && game.clock.running === top}
          active={live && game.turn === top}
        />
        <Board
          fen={shownFen}
          orientation={orientation}
          movable={live && shownPly === null ? myColor : null}
          onMove={(uci) => move(uci)}
          lastMove={lastMove}
          theme={settings.boardTheme}
          premoves={settings.premoves}
          autoQueen={settings.autoQueen}
          showCoordinates={settings.showCoordinates}
        />
        <PlayerBar
          player={game[bottom]}
          color={bottom}
          clock={game.clock}
          running={live && game.clock.running === bottom}
          active={live && game.turn === bottom}
        />
      </section>

      <aside className="flex flex-col gap-4">
        <div className="card p-4">
          <p className="text-sm text-ink-300">
            {timeControlLabel(game.clock.initial, game.clock.increment)} · {game.rated ? 'Rated' : 'Casual'}{' '}
            {game.perf}
            {vsEngine && ' · vs Gambyte Engine'}
          </p>
          {!live && <GameResult game={game} />}
          {game.drawOffer && live && myColor && game.drawOffer !== myColor && (
            <div className="mt-3 flex items-center justify-between rounded-lg bg-ink-800 p-2 text-sm">
              Your opponent offers a draw
              <span className="flex gap-1">
                <button className="btn-primary px-3 py-1" onClick={() => action('draw')}>Accept</button>
                <button className="btn-secondary px-3 py-1" onClick={() => action('draw/decline')}>Decline</button>
              </span>
            </div>
          )}
          {error && <p className="mt-2 text-sm text-red-300">{error}</p>}
        </div>

        <div className="card p-3">
          <MoveList sans={game.sans} current={shownPly ?? game.sans.length - 1} onSelect={setShownPly} />
        </div>

        <div className="card flex flex-wrap gap-2 p-3">
          {live && myColor && (
            <>
              {game.moves.length < 2 ? (
                <button className="btn-secondary" onClick={() => action('abort')}>
                  <X size={16} /> Abort
                </button>
              ) : confirmResign ? (
                <button className="btn bg-red-600 text-white hover:bg-red-500" onClick={() => { setConfirmResign(false); void action('resign'); }}>
                  <Flag size={16} /> Confirm resign
                </button>
              ) : (
                <button className="btn-secondary" onClick={() => setConfirmResign(true)}>
                  <Flag size={16} /> Resign
                </button>
              )}
              {!vsEngine && (
                <button className="btn-secondary" onClick={() => action('draw')} disabled={game.drawOffer === myColor}>
                  <Handshake size={16} /> {game.drawOffer === myColor ? 'Draw offered' : 'Offer draw'}
                </button>
              )}
              {vsEngine && !game.rated && (
                <button className="btn-secondary" onClick={() => action('takeback')}>
                  <Undo2 size={16} /> Takeback
                </button>
              )}
            </>
          )}
          {!live && myColor && (
            <>
              <button className="btn-primary" onClick={() => action('rematch')}>
                <Repeat size={16} /> {game.rematchOffer && game.rematchOffer !== myColor ? 'Accept rematch' : 'Rematch'}
              </button>
              <Link to={`/review/${game.id}`} className="btn-secondary">
                <Sparkles size={16} /> Game review
              </Link>
            </>
          )}
          {!live && (
            <>
              <Link to={`/analysis?game=${game.id}`} className="btn-secondary">
                <Search size={16} /> Analyse
              </Link>
              <a href={`/api/games/${game.id}/pgn`} className="btn-secondary">
                <Download size={16} /> PGN
              </a>
              {myColor && (
                <Link to="/" className="btn-secondary">
                  <RotateCcw size={16} /> New game
                </Link>
              )}
            </>
          )}
          <button className="btn-ghost" onClick={() => setFlipped((f) => !f)} aria-label="Flip board">
            <FlipVertical2 size={16} />
          </button>
        </div>

        {!vsEngine && myColor && <GameChat messages={chat} onSend={say} />}
      </aside>
    </div>
  );
}

function GameResult({ game }: { game: GameView }) {
  const score = game.winner === 'white' ? '1-0' : game.winner === 'black' ? '0-1' : game.status === 'ABORTED' ? '' : '½-½';
  return (
    <div className="mt-3 rounded-lg bg-ink-800 p-3 text-center">
      {score && <p className="text-2xl font-bold">{score}</p>}
      <p className="text-sm text-ink-300">{game.termination}</p>
    </div>
  );
}

function opposite(c: Color): Color {
  return c === 'white' ? 'black' : 'white';
}
