package phase3;

import phase1.Board;
import phase1.Move;
import phase1.MoveFlag;
import phase1.Piece;
import phase1.Square;
import phase2.MoveGenerator;
import phase2.MoveList;
import phase2.Position;

/**
 * Standard Algebraic Notation, the human move format used in PGN files and on every chess site:
 * {@code e4}, {@code Nf3}, {@code exd5}, {@code O-O}, {@code e8=Q+}, {@code Raxd1#}.
 *
 * <ul>
 *   <li>Piece letter (none for pawns), then the target square.
 *   <li>{@code x} for captures; pawn captures start with the pawn's file ({@code exd5}).
 *   <li><b>Disambiguation:</b> if two identical pieces can reach the same square, add the file
 *       ({@code Nbd2}), else the rank ({@code R1e2}), else both ({@code Qh4e1}).
 *   <li>{@code =Q} for promotion, {@code +} for check, {@code #} for checkmate.
 * </ul>
 */
public final class San {

  private San() {}

  /** SAN text for a legal move in this position (the position is unchanged afterwards). */
  public static String toSan(Position position, int move) {
    Board board = position.board();
    int from = Move.start(move);
    int to = Move.target(move);
    int flag = Move.flag(move);
    int piece = board.pieceAt(from);
    StringBuilder sb = new StringBuilder();

    if (flag == MoveFlag.CASTLE) {
      sb.append(Square.file(to) == 6 ? "O-O" : "O-O-O");
    } else {
      boolean capture = board.pieceAt(to) != Piece.NONE || flag == MoveFlag.EN_PASSANT;
      if (Piece.type(piece) == Piece.PAWN) {
        if (capture) {
          sb.append((char) ('a' + Square.file(from))).append('x');
        }
        sb.append(Square.name(to));
        if (MoveFlag.isPromotion(flag)) {
          sb.append('=').append(Character.toUpperCase(Piece.toChar(MoveFlag.promotionType(flag))));
        }
      } else {
        sb.append(Character.toUpperCase(Piece.toChar(piece)));
        sb.append(disambiguation(position, move, piece));
        if (capture) {
          sb.append('x');
        }
        sb.append(Square.name(to));
      }
    }

    position.makeMove(move);
    if (position.inCheck()) {
      MoveList replies = new MoveList();
      MoveGenerator.generate(position, replies);
      sb.append(replies.size() == 0 ? '#' : '+');
    }
    position.unmakeMove(move);
    return sb.toString();
  }

  private static String disambiguation(Position position, int move, int piece) {
    int from = Move.start(move);
    int to = Move.target(move);
    MoveList moves = new MoveList();
    MoveGenerator.generate(position, moves);
    boolean ambiguous = false;
    boolean sameFile = false;
    boolean sameRank = false;
    for (int i = 0; i < moves.size(); i++) {
      int other = moves.get(i);
      int otherFrom = Move.start(other);
      if (otherFrom != from
          && Move.target(other) == to
          && position.board().pieceAt(otherFrom) == piece) {
        ambiguous = true;
        sameFile |= Square.file(otherFrom) == Square.file(from);
        sameRank |= Square.rank(otherFrom) == Square.rank(from);
      }
    }
    if (!ambiguous) {
      return "";
    }
    if (!sameFile) {
      return String.valueOf((char) ('a' + Square.file(from)));
    }
    if (!sameRank) {
      return String.valueOf((char) ('1' + Square.rank(from)));
    }
    return Square.name(from);
  }

  /**
   * Finds the legal move written as {@code san} (check marks and "!?" annotations are ignored,
   * "0-0" is accepted for castling).
   *
   * @return the move, or {@link Move#NULL} if no legal move matches
   */
  public static int parse(Position position, String san) {
    String wanted = clean(san);
    MoveList moves = new MoveList();
    MoveGenerator.generate(position, moves);
    for (int i = 0; i < moves.size(); i++) {
      if (clean(toSan(position, moves.get(i))).equals(wanted)) {
        return moves.get(i);
      }
    }
    return Move.NULL;
  }

  private static String clean(String san) {
    return san.trim().replace("0-0-0", "O-O-O").replace("0-0", "O-O").replaceAll("[+#!?]", "");
  }
}
