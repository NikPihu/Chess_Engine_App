package phase3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import phase1.FenParser;
import phase1.Move;
import phase2.Position;
import phase2.UciMoveParser;

/**
 * A game = a start position + the list of moves played. From those two things everything else can
 * be rebuilt: the current position (with full history for repetition detection), the result, and
 * the PGN. Used by the UCI program and the board viewer.
 */
public final class Game {

  private final String startFen;
  private final List<Integer> moves = new ArrayList<>();
  private Position position;

  public Game() {
    this(FenParser.START_POSITION);
  }

  public Game(String startFen) {
    this.position = Position.fromFen(startFen); // validates the FEN (throws FenException)
    this.startFen = position.toFen();
  }

  public String startFen() {
    return startFen;
  }

  /** The current position. Treat it as read-only: play moves through {@link #play}. */
  public Position position() {
    return position;
  }

  public List<Integer> moves() {
    return Collections.unmodifiableList(moves);
  }

  /** A fresh Position at the current point with the full move history, safe to search on. */
  public Position copyOfPosition() {
    Position copy = Position.fromFen(startFen);
    for (int move : moves) {
      copy.makeMove(move);
    }
    return copy;
  }

  /**
   * Plays a move given in UCI text ("e2e4").
   *
   * @return false (and changes nothing) if the move is not legal
   */
  public boolean play(String uciMove) {
    int move = UciMoveParser.parse(position, uciMove);
    if (move == Move.NULL) {
      return false;
    }
    play(move);
    return true;
  }

  /** Plays a move that is known to be legal. */
  public void play(int move) {
    position.makeMove(move);
    moves.add(move);
  }

  /** Takes back the last move; returns false if there is none. */
  public boolean undo() {
    if (moves.isEmpty()) {
      return false;
    }
    position.unmakeMove(moves.remove(moves.size() - 1));
    return true;
  }

  public GameResult result() {
    return GameRules.result(position);
  }

  /** The moves so far in SAN, e.g. ["e4", "e5", "Nf3"]. */
  public List<String> sanMoves() {
    Position replay = Position.fromFen(startFen);
    List<String> san = new ArrayList<>();
    for (int move : moves) {
      san.add(San.toSan(replay, move));
      replay.makeMove(move);
    }
    return san;
  }

  public String toPgn(Map<String, String> tags) {
    return PgnWriter.write(startFen, moves, tags);
  }
}
