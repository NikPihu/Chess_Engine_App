package phase1;

/**
 * A move packed into 16 bits of an {@code int}:
 *
 * <pre>
 *   15 14 13 12 | 11 10 9 8 7 6 | 5 4 3 2 1 0
 *      flag     |    target     |    start
 *   (FFFF TTTTTT SSSSSS, as in Lague's video)
 * </pre>
 *
 * Moves are plain ints, not objects: the search creates millions per second, and ints cost no
 * allocation or garbage collection. {@link #NULL} (0) means "no move" (a1→a1 is never legal).
 */
public final class Move {

  public static final int NULL = 0;

  private static final int SQUARE_MASK = 0x3F; // 6 bits
  private static final int TARGET_SHIFT = 6;
  private static final int FLAG_SHIFT = 12;

  private Move() {}

  public static int of(int start, int target) {
    return of(start, target, MoveFlag.NONE);
  }

  public static int of(int start, int target, int flag) {
    return start | (target << TARGET_SHIFT) | (flag << FLAG_SHIFT);
  }

  public static int start(int move) {
    return move & SQUARE_MASK;
  }

  public static int target(int move) {
    return (move >>> TARGET_SHIFT) & SQUARE_MASK;
  }

  public static int flag(int move) {
    return move >>> FLAG_SHIFT;
  }

  public static boolean isPromotion(int move) {
    return MoveFlag.isPromotion(flag(move));
  }

  /** UCI text: "e2e4", promotions add the piece letter: "e7e8q". "0000" for {@link #NULL}. */
  public static String toUci(int move) {
    if (move == NULL) {
      return "0000";
    }
    String text = Square.name(start(move)) + Square.name(target(move));
    if (isPromotion(move)) {
      text += Character.toLowerCase(Piece.toChar(MoveFlag.promotionType(flag(move))));
    }
    return text;
  }

  /** Human-readable form for debugging, e.g. "e7e8q (flag 4)". */
  public static String describe(int move) {
    return toUci(move) + " (flag " + flag(move) + ")";
  }
}
