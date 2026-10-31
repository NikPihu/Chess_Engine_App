package phase1;

/**
 * Draws a board as ASCII art plus its state, for the UCI {@code d} command and debugging. White is
 * at the bottom; uppercase letters are white pieces, lowercase are black, '.' is empty.
 */
public final class BoardPrinter {

  private static final String BORDER = "   +---+---+---+---+---+---+---+---+";
  private static final String FILES = "     a   b   c   d   e   f   g   h";

  private BoardPrinter() {}

  public static String print(Board board) {
    StringBuilder sb = new StringBuilder();
    sb.append(FILES).append('\n').append(BORDER).append('\n');
    for (int rank = 7; rank >= 0; rank--) {
      sb.append(' ').append(rank + 1).append(" |");
      for (int file = 0; file < 8; file++) {
        sb.append(' ').append(Piece.toChar(board.pieceAt(Square.of(file, rank)))).append(" |");
      }
      sb.append(' ').append(rank + 1).append('\n').append(BORDER).append('\n');
    }
    sb.append(FILES).append("\n\n");

    sb.append("FEN:        ").append(FenWriter.write(board)).append('\n');
    sb.append("Side:       ").append(Colour.name(board.sideToMove())).append('\n');
    sb.append("Castling:   ").append(CastlingRights.toFen(board.castlingRights())).append('\n');
    sb.append("En passant: ").append(Square.name(board.enPassantSquare())).append('\n');
    sb.append("Clocks:     halfmove ")
        .append(board.halfmoveClock())
        .append(", fullmove ")
        .append(board.fullmoveNumber())
        .append('\n');
    sb.append("Zobrist:    ").append(String.format("0x%016X", board.zobristKey())).append('\n');
    return sb.toString();
  }
}
