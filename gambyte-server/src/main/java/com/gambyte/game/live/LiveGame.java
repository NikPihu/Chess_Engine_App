package com.gambyte.game.live;

import com.gambyte.common.ApiException;
import com.gambyte.game.domain.GameStatus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import phase1.Board;
import phase1.Colour;
import phase1.Piece;
import phase2.Position;
import phase2.UciMoveParser;
import phase3.Game;
import phase3.GameResult;
import phase3.GameRules;
import phase3.San;

/**
 * A game in progress, held in server memory. This is the authority for the position, the moves
 * and the clocks; Postgres only gets the finished game.
 *
 * <p><b>Thread safety:</b> two players (and the engine) may act at the same instant, so every
 * public method that reads or changes state is {@code synchronized} on this object. Locking per
 * game (not one global lock) lets thousands of games run in parallel.
 *
 * <p>Chess rules come from chess_engine_v1 ({@link Game}, {@link GameRules}, {@link San}), the
 * same code our engine was proven on with perft, so legality has one source of truth.
 */
public final class LiveGame {

  /** One side of the board. {@code engineLevel} is set when this side is the Gambyte Engine. */
  public record Player(
      long userId,
      String username,
      String title,
      Integer rating,
      boolean provisional,
      Integer engineLevel) {
    public boolean isEngine() {
      return engineLevel != null;
    }
  }

  /** Something said in the game chat. */
  public record ChatLine(String username, String text, long at) {}

  /** Before both players have moved, each has this long to make their first move. */
  public static final long FIRST_MOVE_MS = 30_000;

  private final String id;
  private final Player white;
  private final Player black;
  private final String perf;
  private final boolean rated;
  private final String source;
  private final Long tournamentId;
  private final Game board;
  private final ChessClock clock;
  private final List<String> moves = new ArrayList<>();
  private final List<String> sans = new ArrayList<>();
  private final List<Integer> moveTimes = new ArrayList<>();
  private final Deque<ChatLine> chat = new ArrayDeque<>();
  private final long createdAt;

  private GameStatus status = GameStatus.STARTED;
  private String winner;
  private String termination;
  private int version;
  private int drawOfferBy = -1;
  private int rematchOfferBy = -1;
  private long lastMoveAt;
  /** Client focus losses reported per colour (a fair-play signal, see fairplay package). */
  private final int[] blurs = new int[2];

  public LiveGame(
      String id,
      Player white,
      Player black,
      String perf,
      boolean rated,
      String source,
      Long tournamentId,
      String startFen,
      long initialMs,
      long incrementMs,
      long now) {
    this.id = id;
    this.white = white;
    this.black = black;
    this.perf = perf;
    this.rated = rated;
    this.source = source;
    this.tournamentId = tournamentId;
    this.board = new Game(startFen);
    this.clock = new ChessClock(initialMs, incrementMs);
    this.createdAt = now;
    this.lastMoveAt = now;
  }

  // ------------------------------------------------------------------ moves

  /** What a successful move changed, for the event sent to the players. */
  public record MoveResult(String uci, String san, String fen, int ply, int version) {}

  /**
   * Plays a move for {@code colour} after checking it is that side's turn and the move is legal.
   * Also detects the end of the game (mate, stalemate, draws).
   */
  public synchronized MoveResult move(int colour, String uci, long now, long lagMs) {
    requireRunning();
    if (sideToMove() != colour) throw ApiException.badRequest("not_your_turn", "Not your turn");
    Position position = board.position();
    int move = UciMoveParser.parse(position, uci);
    if (move == phase1.Move.NULL) throw ApiException.badRequest("illegal_move", "Illegal move");
    if (clock.flagged(now) == colour) {
      flag(colour);
      throw ApiException.badRequest("time_out", "Your time ran out");
    }
    String san = San.toSan(position, move);
    String canonicalUci = phase1.Move.toUci(move);

    // Clocks only start once both sides have made their first move (as on Lichess).
    long think;
    if (moves.size() >= 1 && !clock.isUntimed()) {
      think = clock.press(colour, now, lagMs);
    } else {
      think = now - lastMoveAt;
      if (moves.size() == 1 && !clock.isUntimed()) clock.start(Colour.opposite(colour), now);
    }
    board.play(move);
    moves.add(canonicalUci);
    sans.add(san);
    moveTimes.add((int) Math.min(think, Integer.MAX_VALUE));
    lastMoveAt = now;
    drawOfferBy = -1; // a move declines any pending draw offer
    version++;

    GameResult result = board.result();
    switch (result) {
      case WHITE_WINS -> end(GameStatus.MATE, "white", "White wins by checkmate");
      case BLACK_WINS -> end(GameStatus.MATE, "black", "Black wins by checkmate");
      case STALEMATE -> end(GameStatus.STALEMATE, null, "Draw by stalemate");
      case FIFTY_MOVE_RULE -> end(GameStatus.DRAW, null, "Draw by the 50-move rule");
      case THREEFOLD_REPETITION -> end(GameStatus.DRAW, null, "Draw by threefold repetition");
      case INSUFFICIENT_MATERIAL -> end(GameStatus.DRAW, null, "Draw by insufficient material");
      default -> {}
    }
    return new MoveResult(canonicalUci, san, fen(), moves.size(), version);
  }

  /** Undo the last two plies (only used in casual games against the computer). */
  public synchronized void takeback(int colour) {
    requireRunning();
    int undo = sideToMove() == colour ? 2 : 1; // back to a position where it's colour's turn
    if (moves.size() < undo) throw ApiException.badRequest("no_moves", "Nothing to take back");
    for (int i = 0; i < undo; i++) {
      board.undo();
      moves.removeLast();
      sans.removeLast();
      moveTimes.removeLast();
    }
    version++;
  }

  // ------------------------------------------------------------------ endings

  public synchronized void resign(int colour) {
    requireRunning();
    String w = colour == Colour.WHITE ? "black" : "white";
    end(GameStatus.RESIGN, w, capital(Colour.name(colour)) + " resigned");
  }

  /** Aborting is allowed until both players have moved; the game then doesn't count. */
  public synchronized void abort(int colour) {
    requireRunning();
    if (moves.size() >= 2) {
      throw ApiException.badRequest("too_late_to_abort", "The game can only be aborted at the start");
    }
    end(GameStatus.ABORTED, null, "Game aborted");
  }

  public synchronized void offerDraw(int colour) {
    requireRunning();
    if (drawOfferBy == Colour.opposite(colour)) {
      end(GameStatus.DRAW, null, "Draw by agreement");
    } else {
      drawOfferBy = colour;
      version++;
    }
  }

  public synchronized void declineDraw(int colour) {
    if (drawOfferBy == Colour.opposite(colour)) {
      drawOfferBy = -1;
      version++;
    }
  }

  /** The flag fell for {@code colour}: they lose unless the opponent cannot possibly mate. */
  public synchronized void flag(int colour) {
    if (status.isOver()) return;
    clock.stop(System.currentTimeMillis());
    Board b = board.position().board();
    if (cannotMate(b, Colour.opposite(colour))) {
      end(GameStatus.TIMEOUT, null, capital(Colour.name(colour)) + " ran out of time - draw (insufficient material)");
    } else {
      String w = colour == Colour.WHITE ? "black" : "white";
      end(GameStatus.TIMEOUT, w, capital(Colour.name(colour)) + " ran out of time");
    }
  }

  /** No first move in time: aborted (if nobody moved) or lost by the absent side otherwise. */
  public synchronized boolean checkFirstMoveTimeout(long now) {
    if (status.isOver() || moves.size() >= 2 || clock.isUntimed()) return false;
    Player mover = moves.isEmpty() ? white : black;
    if (mover.isEngine()) return false;
    if (now - lastMoveAt < FIRST_MOVE_MS) return false;
    end(GameStatus.ABORTED, null, capital(moves.isEmpty() ? "white" : "black") + " did not move - aborted");
    return true;
  }

  /** A player left and did not return; the opponent is awarded the win. */
  public synchronized void abandon(int colour) {
    if (status.isOver()) return;
    String w = colour == Colour.WHITE ? "black" : "white";
    end(GameStatus.ABANDONED, w, capital(Colour.name(colour)) + " left the game");
  }

  /** A moderator ended the game for cheating by {@code colour}. */
  public synchronized void endForCheating(int colour) {
    if (status.isOver()) return;
    String w = colour == Colour.WHITE ? "black" : "white";
    end(GameStatus.CHEAT, w, capital(Colour.name(colour)) + " violated fair play");
  }

  private void end(GameStatus status, String winner, String termination) {
    this.status = status;
    this.winner = winner;
    this.termination = termination;
    this.drawOfferBy = -1;
    clock.stop(System.currentTimeMillis());
    version++;
  }

  /**
   * True if {@code colour} has too little material to ever mate (lone king, or king and one minor
   * piece). Then losing on time is a draw, by the FIDE rules.
   */
  private static boolean cannotMate(Board b, int colour) {
    int majorsOrPawns = 0;
    int minors = 0;
    for (int sq = 0; sq < 64; sq++) {
      int piece = b.pieceAt(sq);
      if (piece == Piece.NONE || Piece.colour(piece) != colour) continue;
      switch (Piece.type(piece)) {
        case Piece.PAWN, Piece.ROOK, Piece.QUEEN -> majorsOrPawns++;
        case Piece.KNIGHT, Piece.BISHOP -> minors++;
        default -> {}
      }
    }
    return majorsOrPawns == 0 && minors <= 1;
  }

  // ------------------------------------------------------------------ rematch, chat, signals

  /** Returns true when both players want a rematch (the caller then creates the new game). */
  public synchronized boolean offerRematch(int colour) {
    if (!status.isOver()) throw ApiException.badRequest("game_running", "The game is still running");
    if (rematchOfferBy == Colour.opposite(colour)) return true;
    rematchOfferBy = colour;
    version++;
    return false;
  }

  public synchronized ChatLine say(String username, String text, long now) {
    ChatLine line = new ChatLine(username, text, now);
    chat.addLast(line);
    while (chat.size() > 100) chat.removeFirst();
    return line;
  }

  public synchronized void recordBlur(int colour) {
    blurs[colour]++;
  }

  // ------------------------------------------------------------------ reading state

  private void requireRunning() {
    if (status.isOver()) throw ApiException.badRequest("game_over", "The game is over");
  }

  /** WHITE (0) or BLACK (1) for a user id, or -1 if they don't play in this game. */
  public int colourOf(long userId) {
    if (white.userId() == userId) return Colour.WHITE;
    if (black.userId() == userId) return Colour.BLACK;
    return -1;
  }

  public synchronized int sideToMove() {
    return board.position().sideToMove();
  }

  public synchronized String fen() {
    return board.position().toFen();
  }

  public String id() {
    return id;
  }

  public Player white() {
    return white;
  }

  public Player black() {
    return black;
  }

  public Player player(int colour) {
    return colour == Colour.WHITE ? white : black;
  }

  public String perf() {
    return perf;
  }

  public boolean rated() {
    return rated;
  }

  public String source() {
    return source;
  }

  public Long tournamentId() {
    return tournamentId;
  }

  public String startFen() {
    return board.startFen();
  }

  public ChessClock clock() {
    return clock;
  }

  public long createdAt() {
    return createdAt;
  }

  public synchronized List<String> moves() {
    return List.copyOf(moves);
  }

  public synchronized List<String> sans() {
    return List.copyOf(sans);
  }

  public synchronized List<Integer> moveTimes() {
    return List.copyOf(moveTimes);
  }

  public synchronized List<ChatLine> chat() {
    return List.copyOf(chat);
  }

  public synchronized GameStatus status() {
    return status;
  }

  public synchronized String winner() {
    return winner;
  }

  public synchronized String termination() {
    return termination;
  }

  public synchronized int version() {
    return version;
  }

  public synchronized int drawOfferBy() {
    return drawOfferBy;
  }

  public synchronized int rematchOfferBy() {
    return rematchOfferBy;
  }

  public synchronized long lastMoveAt() {
    return lastMoveAt;
  }

  public synchronized int[] blurs() {
    return blurs.clone();
  }

  /** Replays stored moves (restoring a game after a server restart). */
  public synchronized void replay(List<String> uciMoves, List<Integer> times, List<String> sanMoves) {
    for (int i = 0; i < uciMoves.size(); i++) {
      board.play(uciMoves.get(i));
      moves.add(uciMoves.get(i));
      sans.add(sanMoves.get(i));
      moveTimes.add(times.get(i));
    }
  }

  private static String capital(String s) {
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}
