package phase1;

/**
 * The 4-bit "what kind of move" field of a {@link Move}. Numbering follows Lague's V2, so every
 * promotion has a flag of 4 or more and "is promotion?" is a single comparison.
 *
 * <p>Captures have no flag of their own: a move is a capture when its target square holds an enemy
 * piece, which the board already knows.
 */
public final class MoveFlag {

  public static final int NONE = 0;
  public static final int EN_PASSANT = 1;
  public static final int CASTLE = 2;
  public static final int PAWN_DOUBLE_PUSH = 3;
  public static final int PROMOTE_QUEEN = 4;
  public static final int PROMOTE_KNIGHT = 5;
  public static final int PROMOTE_ROOK = 6;
  public static final int PROMOTE_BISHOP = 7;

  private MoveFlag() {}

  public static boolean isPromotion(int flag) {
    return flag >= PROMOTE_QUEEN;
  }

  /** Piece type a promotion flag turns the pawn into, or {@link Piece#NONE}. */
  public static int promotionType(int flag) {
    return switch (flag) {
      case PROMOTE_QUEEN -> Piece.QUEEN;
      case PROMOTE_KNIGHT -> Piece.KNIGHT;
      case PROMOTE_ROOK -> Piece.ROOK;
      case PROMOTE_BISHOP -> Piece.BISHOP;
      default -> Piece.NONE;
    };
  }

  /** Inverse of {@link #promotionType}: the flag for promoting to {@code pieceType}. */
  public static int promotionFlag(int pieceType) {
    return switch (pieceType) {
      case Piece.QUEEN -> PROMOTE_QUEEN;
      case Piece.KNIGHT -> PROMOTE_KNIGHT;
      case Piece.ROOK -> PROMOTE_ROOK;
      case Piece.BISHOP -> PROMOTE_BISHOP;
      default -> throw new IllegalArgumentException("Cannot promote to piece type " + pieceType);
    };
  }
}
