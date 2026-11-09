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
    <div className="grid animate-fade gap-6 lg:grid-cols-[minmax(0,1fr)_24rem] xl:gap-10">
      <section className="relative mx-auto w-full max-w-[min(94vw,calc(100vh-17rem))]">
        <div className="pointer-events-none absolute top-1/2 left-1/2 -z-10 h-[70%] w-[70%] -translate-x-1/2 -translate-y-1/2 rounded-full bg-ember-600/10 blur-[100px]" />
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
        <div className="card p-5">
          <div className="flex items-center justify-between">
            <p className="eyebrow">
              {game.rated ? 'Rated' : 'Casual'} · {game.perf} · {timeControlLabel(game.clock.initial, game.clock.increment)}
            </p>
            {live ? (
              <span className="eyebrow flex items-center gap-1.5 text-ember-300"><span className="h-1.5 w-1.5 animate-pulse rounded-full bg-ember-500" /> Live</span>
            ) : (
              <span className="eyebrow">Finished</span>
            )}
          </div>
          <p className="mt-2 text-lg font-light tracking-tight">
            {game.white.username} <span className="text-noir-500">vs</span> {game.black.username}
            {vsEngine && <span className="ml-2 text-sm text-noir-400">· Gambyte Engine</span>}
          </p>
          {!live && <GameResult game={game} />}
          {game.drawOffer && live && myColor && game.drawOffer !== myColor && (
            <div className="mt-4 flex items-center justify-between rounded-2xl border border-white/[0.08] bg-white/[0.04] p-2 pl-4 text-sm">
              Draw offered
              <span className="flex gap-1">
                <button className="btn-primary px-4 py-1.5" onClick={() => action('draw')}>Accept</button>
                <button className="btn-ghost px-3 py-1.5" onClick={() => action('draw/decline')}>Decline</button>
              </span>
            </div>
          )}
          {error && <p className="mt-2 text-sm text-loss">{error}</p>}
        </div>

        <div className="card p-4">
          <p className="eyebrow mb-3">Moves</p>
          <MoveList sans={game.sans} current={shownPly ?? game.sans.length - 1} onSelect={setShownPly} />
        </div>

        <div className="flex flex-wrap gap-2">
          {live && myColor && (
            <>
              {game.moves.length < 2 ? (
                <button className="btn-secondary" onClick={() => action('abort')}>
                  <X size={16} /> Abort
                </button>
              ) : confirmResign ? (
                <button className="btn-danger" onClick={() => { setConfirmResign(false); void action('resign'); }}>
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
              <button className="btn-accent" onClick={() => action('rematch')}>
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
          <button className="btn-secondary ml-auto px-3" onClick={() => setFlipped((f) => !f)} aria-label="Flip board">
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
    <div className="relative mt-4 overflow-hidden rounded-2xl border border-white/[0.08] bg-white/[0.03] p-5 text-center">
      <div className="absolute inset-x-0 -top-10 mx-auto h-24 w-48 rounded-full bg-ember-500/25 blur-3xl" />
      {score && <p className="num relative text-5xl font-extralight tracking-tight">{score}</p>}
      <p className="relative mt-2 text-sm text-noir-300">{game.termination}</p>
    </div>
  );
}

function opposite(c: Color): Color {
  return c === 'white' ? 'black' : 'white';
}
