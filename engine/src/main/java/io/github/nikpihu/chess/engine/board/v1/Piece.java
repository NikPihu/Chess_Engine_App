package io.github.nikpihu.chess.engine.board.v1;

/**
 * A piece is a small int: {@code colour << 3 | type}.
 *
 * <pre>
 *   bit:   3      2 1 0
 *        colour   type
 *
 *   type:  0 none, 1 pawn, 2 knight, 3 bishop, 4 rook, 5 queen, 6 king
 *   white pieces:  1..6   (P N B R Q K)
 *   black pieces:  9..14  (p n b r q k)
 * </pre>
 *
 * <p>Lague's V1 used 5 bits (two colour bits: white = 8, black = 16). His V2 switched to this 4-bit
 * form: the colour is one bit, so {@code colour(p) = p >>> 3} and every piece code is below 16,
 * which makes it a cheap array index ({@code pieceBitboards[piece]}).
 */
public final class Piece {

  // Piece types (the low 3 bits).
  public static final int NONE = 0;
  public static final int PAWN = 1;
  public static final int KNIGHT = 2;
  public static final int BISHOP = 3;
  public static final int ROOK = 4;
  public static final int QUEEN = 5;
  public static final int KING = 6;

  // Full piece codes (colour + type).
  public static final int WHITE_PAWN = make(Colour.WHITE, PAWN);
  public static final int WHITE_KNIGHT = make(Colour.WHITE, KNIGHT);
  public static final int WHITE_BISHOP = make(Colour.WHITE, BISHOP);
  public static final int WHITE_ROOK = make(Colour.WHITE, ROOK);
  public static final int WHITE_QUEEN = make(Colour.WHITE, QUEEN);
  public static final int WHITE_KING = make(Colour.WHITE, KING);
  public static final int BLACK_PAWN = make(Colour.BLACK, PAWN);
  public static final int BLACK_KNIGHT = make(Colour.BLACK, KNIGHT);
  public static final int BLACK_BISHOP = make(Colour.BLACK, BISHOP);
  public static final int BLACK_ROOK = make(Colour.BLACK, ROOK);
  public static final int BLACK_QUEEN = make(Colour.BLACK, QUEEN);
  public static final int BLACK_KING = make(Colour.BLACK, KING);

  /** Size for arrays indexed by piece code (codes 0..14; 0, 7 and 8 are unused). */
  public static final int CODE_COUNT = 15;

  /** All twelve real pieces, handy for loops. */
  public static final int[] ALL = {
    WHITE_PAWN, WHITE_KNIGHT, WHITE_BISHOP, WHITE_ROOK, WHITE_QUEEN, WHITE_KING,
    BLACK_PAWN, BLACK_KNIGHT, BLACK_BISHOP, BLACK_ROOK, BLACK_QUEEN, BLACK_KING
  };

  private static final String TYPE_CHARS = ".pnbrqk";

  private Piece() {}

  public static int make(int colour, int type) {
    return colour << 3 | type;
  }

  public static int type(int piece) {
    return piece & 7;
  }

  public static int colour(int piece) {
    return piece >>> 3;
  }

  public static boolean isValid(int piece) {
    int type = type(piece);
    return piece < CODE_COUNT && type >= PAWN && type <= KING;
  }

  /** FEN letter: uppercase for white, lowercase for black ('P', 'n', ...). '.' for none. */
  public static char toChar(int piece) {
    if (!isValid(piece)) {
      return '.';
    }
    char c = TYPE_CHARS.charAt(type(piece));
    return colour(piece) == Colour.WHITE ? Character.toUpperCase(c) : c;
  }

  /**
   * Parses a FEN letter.
   *
   * @return the piece code, or {@link #NONE} if the letter is not a piece
   */
  public static int fromChar(char c) {
    int type = TYPE_CHARS.indexOf(Character.toLowerCase(c));
    if (type < PAWN) {
      return NONE;
    }
    return make(Character.isUpperCase(c) ? Colour.WHITE : Colour.BLACK, type);
  }
}
