/**
 * TypeScript mirrors of the server's JSON (the records in gambyte-server's *Dtos.java files).
 * Keep the two in sync when an API changes; the Swagger UI at /swagger-ui.html shows the truth.
 */

export type Perf = 'bullet' | 'blitz' | 'rapid' | 'classical' | 'puzzle';
export type Color = 'white' | 'black';

export interface RatingView {
  rating: number;
  deviation: number;
  provisional: boolean;
  games: number;
  best: number;
}

export interface UserRef {
  id: number;
  username: string;
  title?: string;
  premium: boolean;
}

export interface Me {
  id: number;
  username: string;
  email: string;
  emailVerified: boolean;
  role: 'USER' | 'MOD' | 'ADMIN';
  title?: string;
  premium: boolean;
  premiumUntil?: string;
  settings: Settings;
  ratings: Partial<Record<Perf, RatingView>>;
}

/** UI preferences stored on the server (free-form JSON owned by the frontend). */
export interface Settings {
  boardTheme?: 'green' | 'brown' | 'blue' | 'gray';
  sound?: boolean;
  premoves?: boolean;
  autoQueen?: boolean;
  showCoordinates?: boolean;
}

export interface Profile {
  id: number;
  username: string;
  title?: string;
  country?: string;
  bio?: string;
  premium: boolean;
  online: boolean;
  createdAt: string;
  lastSeenAt?: string;
  ratings: Partial<Record<Perf, RatingView>>;
}

export interface PlayerView {
  id: number;
  username: string;
  title?: string;
  rating?: number;
  ratingDiff?: number;
  provisional: boolean;
  engineLevel?: number;
}

export interface ClockView {
  white: number;
  black: number;
  running?: Color;
  initial: number;
  increment: number;
  serverTime: number;
}

export type GameStatus =
  | 'STARTED'
  | 'ABORTED'
  | 'MATE'
  | 'RESIGN'
  | 'STALEMATE'
  | 'TIMEOUT'
  | 'DRAW'
  | 'ABANDONED'
  | 'CHEAT';

export interface GameView {
  id: string;
  white: PlayerView;
  black: PlayerView;
  perf: Perf;
  rated: boolean;
  source: string;
  status: GameStatus;
  winner?: Color;
  termination?: string;
  startFen: string;
  fen: string;
  moves: string[];
  sans: string[];
  moveTimes: number[];
  clock: ClockView;
  turn: Color;
  drawOffer?: Color;
  rematchOffer?: Color;
  version: number;
  tournamentId?: number;
  createdAt: string;
  endedAt?: string;
}

/** Messages on /topic/game/{id}. */
export interface GameEvent {
  t: 'move' | 'end' | 'draw' | 'rematch' | 'chat' | 'takeback' | 'state';
  v: number;
  uci?: string;
  san?: string;
  fen?: string;
  ply?: number;
  clock?: ClockView;
  status?: GameStatus;
  winner?: Color;
  termination?: string;
  by?: string;
  whiteRatingDiff?: number;
  blackRatingDiff?: number;
  text?: string;
  rematchId?: string;
}

export interface GameSummary {
  id: string;
  white: PlayerView;
  black: PlayerView;
  perf: Perf;
  rated: boolean;
  status: GameStatus;
  winner?: Color;
  termination?: string;
  ply: number;
  finalFen?: string;
  initial: number;
  increment: number;
  createdAt: string;
}

export interface GamePage {
  games: GameSummary[];
  nextBefore?: string;
}

export interface LeaderboardEntry {
  rank: number;
  user: UserRef;
  rating: RatingView;
}

/** An error body from the server (RFC 9457 problem detail + our machine-readable code). */
export interface ApiProblem {
  status: number;
  detail?: string;
  code?: string;
}
