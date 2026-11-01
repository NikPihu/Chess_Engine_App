package phase3;

import phase1.Bitboards;
import phase1.Board;
import phase1.Colour;
import phase1.Piece;
import phase2.MoveGenerator;
import phase2.MoveList;
import phase2.Position;

/**
 * Decides whether a game is over.
 *
 * <ul>
 *   <li><b>Checkmate / stalemate:</b> no legal moves; mate if in check, stalemate if not.
 *   <li><b>50-move rule:</b> 100 half-moves without a capture or pawn move.
 *   <li><b>Threefold repetition:</b> the same position (same Zobrist key, same side to move) for
 *       the third time. Only positions since the last capture/pawn move can repeat, so we only look
 *       back {@code halfmoveClock} plies, and only at every second one (same side to move).
 *   <li><b>Insufficient material:</b> nobody can ever mate: K v K, K+minor v K, or only bishops
 *       that all stand on the same square colour.
 * </ul>
 */
public final class GameRules {

  private static final long LIGHT_SQUARES = 0x55AA55AA55AA55AAL;

  private GameRules() {}

  public static GameResult result(Position position) {
    MoveList moves = new MoveList();
    MoveGenerator.generate(position, moves);
    if (moves.size() == 0) {
      if (!position.inCheck()) {
        return GameResult.STALEMATE;
      }
      return position.sideToMove() == Colour.WHITE ? GameResult.BLACK_WINS : GameResult.WHITE_WINS;
    }
    if (position.board().halfmoveClock() >= 100) {
      return GameResult.FIFTY_MOVE_RULE;
    }
    if (repetitionCount(position) >= 3) {
      return GameResult.THREEFOLD_REPETITION;
    }
    if (isInsufficientMaterial(position.board())) {
      return GameResult.INSUFFICIENT_MATERIAL;
    }
    return GameResult.ONGOING;
  }

  /** How many times the current position has occurred (1 = first time). */
  public static int repetitionCount(Position position) {
    long key = position.key();
    int history = position.historySize();
    int oldest = Math.max(0, history - position.board().halfmoveClock());
    int count = 1;
    // keyBefore(i) is the position after i moves; same side to move as now when (history - i) is
    // even.
    for (int i = history - 2; i >= oldest; i -= 2) {
      if (position.keyBefore(i) == key) {
        count++;
      }
    }
    return count;
  }

  public static boolean isInsufficientMaterial(Board board) {
    for (int colour = Colour.WHITE; colour <= Colour.BLACK; colour++) {
      if (board.pieces(Piece.make(colour, Piece.PAWN)) != 0
          || board.pieces(Piece.make(colour, Piece.ROOK)) != 0
          || board.pieces(Piece.make(colour, Piece.QUEEN)) != 0) {
        return false;
      }
    }
    long knights = board.pieces(Piece.WHITE_KNIGHT) | board.pieces(Piece.BLACK_KNIGHT);
    long bishops = board.pieces(Piece.WHITE_BISHOP) | board.pieces(Piece.BLACK_BISHOP);
    if (Bitboards.count(knights | bishops) <= 1) {
      return true; // K v K, or K + one minor v K
    }
    // Only bishops, all on the same colour of square: they can never attack the other colour.
    return knights == 0 && ((bishops & LIGHT_SQUARES) == 0 || (bishops & ~LIGHT_SQUARES) == 0);
  }
}
