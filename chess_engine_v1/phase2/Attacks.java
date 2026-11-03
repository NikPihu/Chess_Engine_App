package phase2;

import phase1.Colour;
import phase1.Square;

/**
 * Precomputed attack tables for the "jumping" pieces: knight, king and pawn.
 *
 * <p>Their attacks never depend on other pieces, so we compute them once for all 64 squares and
 * then a lookup is a single array read: {@code Attacks.KNIGHT[sq]}. This is the "precompute instead
 * of recompute" idea that makes engines fast.
 */
public final class Attacks {

  /** KNIGHT[sq]: squares a knight on sq attacks. */
  public static final long[] KNIGHT = new long[64];

  /** KING[sq]: squares a king on sq attacks. */
  public static final long[] KING = new long[64];

  /** PAWN[colour][sq]: squares a pawn of that colour on sq attacks (its two diagonal captures). */
  public static final long[][] PAWN = new long[2][64];

  private static final int[][] KNIGHT_JUMPS = {
    {1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
  };
  private static final int[][] KING_STEPS = {
    {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
  };

  static {
    for (int sq = 0; sq < 64; sq++) {
      KNIGHT[sq] = jumps(sq, KNIGHT_JUMPS);
      KING[sq] = jumps(sq, KING_STEPS);
      PAWN[Colour.WHITE][sq] = jumps(sq, new int[][] {{-1, 1}, {1, 1}});
      PAWN[Colour.BLACK][sq] = jumps(sq, new int[][] {{-1, -1}, {1, -1}});
    }
  }

  private Attacks() {}

  /** Squares reached from sq by each (fileDelta, rankDelta) that stays on the board. */
  private static long jumps(int sq, int[][] deltas) {
    long bb = 0;
    for (int[] d : deltas) {
      int file = Square.file(sq) + d[0];
      int rank = Square.rank(sq) + d[1];
      if (file >= 0 && file < 8 && rank >= 0 && rank < 8) {
        bb |= 1L << Square.of(file, rank);
      }
    }
    return bb;
  }
}
