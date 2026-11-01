package phase2;

import phase1.Move;

/**
 * Turns UCI move text ("e2e4", "e7e8q", "e1g1") into our 16-bit move.
 *
 * <p>The text alone doesn't say whether a move is castling, en passant or a double push, so we
 * generate the legal moves and pick the one whose text matches. That also rejects illegal input.
 */
public final class UciMoveParser {

  private UciMoveParser() {}

  /**
   * @return the legal move matching {@code text}, or {@link Move#NULL} if there is none
   */
  public static int parse(Position position, String text) {
    MoveList moves = new MoveList();
    MoveGenerator.generate(position, moves);
    String wanted = text.toLowerCase();
    for (int i = 0; i < moves.size(); i++) {
      if (Move.toUci(moves.get(i)).equals(wanted)) {
        return moves.get(i);
      }
    }
    return Move.NULL;
  }
}
