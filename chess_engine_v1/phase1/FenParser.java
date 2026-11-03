package phase1;

/**
 * Reads a FEN string into a {@link Board}.
 *
 * <pre>
 * rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1
 * └──────────── placement ────────────────────┘ │ │    │  │ └ fullmove number
 *                                               │ │    │  └ halfmove clock
 *                                               │ │    └ en-passant square
 *                                               │ └ castling rights
 *                                               └ side to move
 * </pre>
 *
 * The two clock fields are optional (many test positions omit them); they default to "0 1".
 *
 * <p>Checks done here: correct shape of every field, exactly one king per side, no pawns on the
 * first or last rank, and an en-passant square on the right rank for the side to move. Deeper
 * legality (e.g. "the side not to move is in check") needs attack detection and comes in Phase 2.
 */
public final class FenParser {

  public static final String START_POSITION =
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

  private FenParser() {}

  public static Board parse(String fen) {
    if (fen == null) {
      throw new FenException("null", "FEN is null");
    }
    String[] fields = fen.trim().split("\\s+");
    if (fields.length != 4 && fields.length != 6) {
      throw new FenException(fen, "expected 4 or 6 fields, got " + fields.length);
    }

    Board board = new Board();
    parsePlacement(fen, fields[0], board);
    board.setSideToMove(parseSide(fen, fields[1]));

    int castling = CastlingRights.fromFen(fields[2]);
    if (castling < 0) {
      throw new FenException(fen, "bad castling field '" + fields[2] + "'");
    }
    board.setCastlingRights(castling);

    board.setEnPassantSquare(parseEnPassant(fen, fields[3], board.sideToMove()));

    if (fields.length == 6) {
      board.setHalfmoveClock(parseNumber(fen, fields[4], "halfmove clock", 0));
      board.setFullmoveNumber(parseNumber(fen, fields[5], "fullmove number", 1));
    }
    return board;
  }

  private static void parsePlacement(String fen, String placement, Board board) {
    String[] ranks = placement.split("/", -1);
    if (ranks.length != 8) {
      throw new FenException(fen, "expected 8 ranks, got " + ranks.length);
    }
    // FEN lists rank 8 first, so ranks[0] is rank index 7.
    for (int i = 0; i < 8; i++) {
      int rank = 7 - i;
      int file = 0;
      for (char c : ranks[i].toCharArray()) {
        if (c >= '1' && c <= '8') {
          file += c - '0';
        } else {
          int piece = Piece.fromChar(c);
          if (piece == Piece.NONE) {
            throw new FenException(fen, "unknown piece letter '" + c + "'");
          }
          if (file > 7) {
            throw new FenException(fen, "rank " + (rank + 1) + " has more than 8 squares");
          }
          if (Piece.type(piece) == Piece.PAWN && (rank == 0 || rank == 7)) {
            throw new FenException(fen, "pawn on rank " + (rank + 1));
          }
          board.putPiece(piece, Square.of(file, rank));
          file++;
        }
      }
      if (file != 8) {
        throw new FenException(fen, "rank " + (rank + 1) + " has " + file + " squares, not 8");
      }
    }
    for (int colour = Colour.WHITE; colour <= Colour.BLACK; colour++) {
      int kings = Long.bitCount(board.pieces(Piece.make(colour, Piece.KING)));
      if (kings != 1) {
        throw new FenException(fen, Colour.name(colour) + " has " + kings + " kings");
      }
    }
  }

  private static int parseSide(String fen, String side) {
    return switch (side) {
      case "w" -> Colour.WHITE;
      case "b" -> Colour.BLACK;
      default -> throw new FenException(fen, "side to move must be 'w' or 'b', got '" + side + "'");
    };
  }

  private static int parseEnPassant(String fen, String text, int sideToMove) {
    if (text.equals("-")) {
      return Square.NONE;
    }
    int square = Square.parse(text);
    if (square == Square.NONE) {
      throw new FenException(fen, "bad en-passant square '" + text + "'");
    }
    // The square is behind a pawn that just moved two: rank 6 if black just moved, else rank 3.
    int expectedRank = sideToMove == Colour.WHITE ? 5 : 2;
    if (Square.rank(square) != expectedRank) {
      throw new FenException(
          fen, "en-passant square " + text + " is impossible with " + Colour.name(sideToMove));
    }
    return square;
  }

  private static int parseNumber(String fen, String text, String what, int minimum) {
    try {
      int value = Integer.parseInt(text);
      if (value < minimum) {
        throw new FenException(fen, what + " must be at least " + minimum);
      }
      return value;
    } catch (NumberFormatException e) {
      throw new FenException(fen, what + " is not a number: '" + text + "'");
    }
  }
}
