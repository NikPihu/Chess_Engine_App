package phase1;

import java.util.Arrays;

/**
 * The position: where every piece is, plus the game state FEN describes (side to move, castling
 * rights, en-passant square, move clocks) and the Zobrist hash.
 *
 * <p><b>Two views of the same pieces, always kept in sync:</b>
 *
 * <ul>
 *   <li><b>Bitboards</b> ({@code long} per piece, per colour, and all pieces): fast for "set"
 *       questions like "all squares with an enemy piece".
 *   <li><b>Mailbox</b> ({@code int[64]}): fast for "what is on e4?" in O(1).
 * </ul>
 *
 * <p>All piece changes go through {@link #putPiece} and {@link #removePiece}, and all state changes
 * through the setters. Each of them also updates the Zobrist key, so {@link #zobristKey()} is
 * always correct without rehashing. Making and unmaking moves arrives in Phase 2 on top of these.
 */
public final class Board {

  private final long[] pieceBitboards = new long[Piece.CODE_COUNT];
  private final long[] colourBitboards = new long[2];
  private long occupied;
  private final int[] mailbox = new int[Square.COUNT];

  private int sideToMove = Colour.WHITE;
  private int castlingRights = CastlingRights.NONE;
  private int enPassantSquare = Square.NONE;
  private int halfmoveClock = 0;
  private int fullmoveNumber = 1;

  private long zobristKey = 0;

  /** An empty board, white to move, no rights. Build positions with FenParser or putPiece. */
  public Board() {}

  /** A deep copy: changing the copy never affects this board. */
  public Board copy() {
    Board b = new Board();
    System.arraycopy(pieceBitboards, 0, b.pieceBitboards, 0, pieceBitboards.length);
    System.arraycopy(colourBitboards, 0, b.colourBitboards, 0, 2);
    System.arraycopy(mailbox, 0, b.mailbox, 0, Square.COUNT);
    b.occupied = occupied;
    b.sideToMove = sideToMove;
    b.castlingRights = castlingRights;
    b.enPassantSquare = enPassantSquare;
    b.halfmoveClock = halfmoveClock;
    b.fullmoveNumber = fullmoveNumber;
    b.zobristKey = zobristKey;
    return b;
  }

  // ---------------------------------------------------------------- pieces

  /** Places {@code piece} on an empty {@code square}. */
  public void putPiece(int piece, int square) {
    if (mailbox[square] != Piece.NONE) {
      throw new IllegalStateException("Square " + Square.name(square) + " is not empty");
    }
    long bit = Bitboards.of(square);
    pieceBitboards[piece] |= bit;
    colourBitboards[Piece.colour(piece)] |= bit;
    occupied |= bit;
    mailbox[square] = piece;
    zobristKey ^= Zobrist.PIECE_SQUARE[piece][square];
  }

  /**
   * Removes whatever stands on {@code square}.
   *
   * @return the removed piece, or {@link Piece#NONE} if the square was empty
   */
  public int removePiece(int square) {
    int piece = mailbox[square];
    if (piece == Piece.NONE) {
      return Piece.NONE;
    }
    long notBit = ~Bitboards.of(square);
    pieceBitboards[piece] &= notBit;
    colourBitboards[Piece.colour(piece)] &= notBit;
    occupied &= notBit;
    mailbox[square] = Piece.NONE;
    zobristKey ^= Zobrist.PIECE_SQUARE[piece][square];
    return piece;
  }

  public int pieceAt(int square) {
    return mailbox[square];
  }

  /** Bitboard of one piece code, e.g. {@code pieces(Piece.WHITE_KNIGHT)}. */
  public long pieces(int piece) {
    return pieceBitboards[piece];
  }

  /** Bitboard of every piece of one colour. */
  public long piecesOf(int colour) {
    return colourBitboards[colour];
  }

  public long occupied() {
    return occupied;
  }

  /** Square of the king of {@code colour}, or {@link Square#NONE} if there is none. */
  public int kingSquare(int colour) {
    long king = pieceBitboards[Piece.make(colour, Piece.KING)];
    return king == 0 ? Square.NONE : Bitboards.lowestSquare(king);
  }

  // ---------------------------------------------------------------- game state

  public int sideToMove() {
    return sideToMove;
  }

  public void setSideToMove(int colour) {
    if (colour != sideToMove) {
      sideToMove = colour;
      zobristKey ^= Zobrist.BLACK_TO_MOVE;
    }
  }

  public int castlingRights() {
    return castlingRights;
  }

  public void setCastlingRights(int rights) {
    zobristKey ^= Zobrist.CASTLING[castlingRights] ^ Zobrist.CASTLING[rights];
    castlingRights = rights;
  }

  /** The square a pawn could capture onto en passant, or {@link Square#NONE}. */
  public int enPassantSquare() {
    return enPassantSquare;
  }

  public void setEnPassantSquare(int square) {
    if (enPassantSquare != Square.NONE) {
      zobristKey ^= Zobrist.EN_PASSANT_FILE[Square.file(enPassantSquare)];
    }
    enPassantSquare = square;
    if (square != Square.NONE) {
      zobristKey ^= Zobrist.EN_PASSANT_FILE[Square.file(square)];
    }
  }

  /** Half-moves since the last capture or pawn move (for the 50-move rule). */
  public int halfmoveClock() {
    return halfmoveClock;
  }

  public void setHalfmoveClock(int halfmoveClock) {
    this.halfmoveClock = halfmoveClock;
  }

  /** Starts at 1 and goes up after each black move. */
  public int fullmoveNumber() {
    return fullmoveNumber;
  }

  public void setFullmoveNumber(int fullmoveNumber) {
    this.fullmoveNumber = fullmoveNumber;
  }

  /** The Zobrist hash, kept up to date incrementally. See {@link Zobrist}. */
  public long zobristKey() {
    return zobristKey;
  }

  // ---------------------------------------------------------------- equality

  /** Two boards are equal when pieces and all game state match. */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Board other)) {
      return false;
    }
    return Arrays.equals(mailbox, other.mailbox)
        && Arrays.equals(pieceBitboards, other.pieceBitboards)
        && Arrays.equals(colourBitboards, other.colourBitboards)
        && occupied == other.occupied
        && sideToMove == other.sideToMove
        && castlingRights == other.castlingRights
        && enPassantSquare == other.enPassantSquare
        && halfmoveClock == other.halfmoveClock
        && fullmoveNumber == other.fullmoveNumber
        && zobristKey == other.zobristKey;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(zobristKey) * 31 + halfmoveClock * 7 + fullmoveNumber;
  }
}
