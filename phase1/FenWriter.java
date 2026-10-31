package phase1;

/** Writes a {@link Board} as a FEN string (always all 6 fields). Inverse of {@link FenParser}. */
public final class FenWriter {

  private FenWriter() {}

  public static String write(Board board) {
    StringBuilder sb = new StringBuilder(90);

    for (int rank = 7; rank >= 0; rank--) {
      int emptyRun = 0;
      for (int file = 0; file < 8; file++) {
        int piece = board.pieceAt(Square.of(file, rank));
        if (piece == Piece.NONE) {
          emptyRun++;
        } else {
          if (emptyRun > 0) {
            sb.append(emptyRun);
            emptyRun = 0;
          }
          sb.append(Piece.toChar(piece));
        }
      }
      if (emptyRun > 0) {
        sb.append(emptyRun);
      }
      if (rank > 0) {
        sb.append('/');
      }
    }

    sb.append(' ').append(board.sideToMove() == Colour.WHITE ? 'w' : 'b');
    sb.append(' ').append(CastlingRights.toFen(board.castlingRights()));
    sb.append(' ').append(Square.name(board.enPassantSquare()));
    sb.append(' ').append(board.halfmoveClock());
    sb.append(' ').append(board.fullmoveNumber());
    return sb.toString();
  }
}
