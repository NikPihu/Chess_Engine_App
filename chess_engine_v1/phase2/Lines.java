package phase2;

/**
 * Geometry between two squares, precomputed for all 64 x 64 pairs.
 *
 * <ul>
 *   <li>{@code BETWEEN[a][b]}: squares strictly between a and b if they share a rank, file or
 *       diagonal, else 0. Used for checks: a check by a slider is blocked by moving a piece onto
 *       one of these squares.
 *   <li>{@code LINE[a][b]}: the whole board-wide line through a and b (including both), else 0.
 *       Used for pins: a pinned piece may only move along the line through its king and itself.
 * </ul>
 */
public final class Lines {

  public static final long[][] BETWEEN = new long[64][64];
  public static final long[][] LINE = new long[64][64];

  static {
    for (int a = 0; a < 64; a++) {
      for (int b = 0; b < 64; b++) {
        if (a == b) {
          continue;
        }
        long bitA = 1L << a;
        long bitB = 1L << b;
        if ((RayAttacks.rook(a, 0) & bitB) != 0) {
          BETWEEN[a][b] = RayAttacks.rook(a, bitB) & RayAttacks.rook(b, bitA);
          LINE[a][b] = (RayAttacks.rook(a, 0) & RayAttacks.rook(b, 0)) | bitA | bitB;
        } else if ((RayAttacks.bishop(a, 0) & bitB) != 0) {
          BETWEEN[a][b] = RayAttacks.bishop(a, bitB) & RayAttacks.bishop(b, bitA);
          LINE[a][b] = (RayAttacks.bishop(a, 0) & RayAttacks.bishop(b, 0)) | bitA | bitB;
        }
      }
    }
  }

  private Lines() {}
}
