package phase1;

/**
 * Castling rights packed into 4 bits, so all 16 combinations fit in one small int (and index the 16
 * Zobrist castling keys directly).
 *
 * <pre>
 *   bit 0 (1) = white king side   K
 *   bit 1 (2) = white queen side  Q
 *   bit 2 (4) = black king side   k
 *   bit 3 (8) = black queen side  q
 * </pre>
 *
 * A right only says "the king and that rook have not moved". Whether castling is legal right now
 * (empty squares, no check) is decided by move generation in Phase 2.
 */
public final class CastlingRights {

  public static final int NONE = 0;
  public static final int WHITE_KING_SIDE = 1;
  public static final int WHITE_QUEEN_SIDE = 2;
  public static final int BLACK_KING_SIDE = 4;
  public static final int BLACK_QUEEN_SIDE = 8;
  public static final int ALL = 15;

  private static final String FEN_CHARS = "KQkq";

  private CastlingRights() {}

  public static boolean has(int rights, int right) {
    return (rights & right) != 0;
  }

  /** FEN form, e.g. "KQkq", "Kq" or "-". */
  public static String toFen(int rights) {
    StringBuilder sb = new StringBuilder(4);
    for (int i = 0; i < 4; i++) {
      if ((rights & (1 << i)) != 0) {
        sb.append(FEN_CHARS.charAt(i));
      }
    }
    return sb.isEmpty() ? "-" : sb.toString();
  }

  /**
   * Parses the FEN castling field.
   *
   * @return the rights bitmask, or -1 if the text is not valid
   */
  public static int fromFen(String text) {
    if (text.equals("-")) {
      return NONE;
    }
    int rights = NONE;
    for (int i = 0; i < text.length(); i++) {
      int index = FEN_CHARS.indexOf(text.charAt(i));
      if (index < 0 || (rights & (1 << index)) != 0) {
        return -1; // unknown letter or duplicate
      }
      rights |= 1 << index;
    }
    return text.isEmpty() ? -1 : rights;
  }
}
