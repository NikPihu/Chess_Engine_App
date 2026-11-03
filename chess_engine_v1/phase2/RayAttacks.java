package phase2;

import phase1.Square;

/**
 * Sliding-piece attacks (rook, bishop, queen) the simple way: walk outwards in each direction until
 * the edge of the board or the first piece in the way (that piece is included: it can be captured,
 * or it is our own piece and gets masked out later).
 *
 * <p>Easy to understand and obviously correct, but slow (a loop per direction). {@link Magics} does
 * the same job with one multiplication and one table lookup, and the tests check that both always
 * agree.
 */
public final class RayAttacks {

  private static final int[][] ROOK_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
  private static final int[][] BISHOP_DIRECTIONS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

  private RayAttacks() {}

  public static long rook(int sq, long occupied) {
    return slide(sq, occupied, ROOK_DIRECTIONS);
  }

  public static long bishop(int sq, long occupied) {
    return slide(sq, occupied, BISHOP_DIRECTIONS);
  }

  private static long slide(int sq, long occupied, int[][] directions) {
    long attacks = 0;
    for (int[] d : directions) {
      int file = Square.file(sq) + d[0];
      int rank = Square.rank(sq) + d[1];
      while (file >= 0 && file < 8 && rank >= 0 && rank < 8) {
        long bit = 1L << Square.of(file, rank);
        attacks |= bit;
        if ((occupied & bit) != 0) {
          break; // blocked: stop after this square
        }
        file += d[0];
        rank += d[1];
      }
    }
    return attacks;
  }
}
