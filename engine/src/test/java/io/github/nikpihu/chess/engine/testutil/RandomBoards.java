package io.github.nikpihu.chess.engine.testutil;

import io.github.nikpihu.chess.engine.board.v1.Board;
import io.github.nikpihu.chess.engine.board.v1.Colour;
import io.github.nikpihu.chess.engine.board.v1.Piece;
import io.github.nikpihu.chess.engine.board.v1.Square;
import java.util.Random;

/**
 * Builds random (but FEN-valid) boards for property tests: one king per side, up to 30 other
 * pieces, no pawns on the back ranks, en passant only on the correct rank. Same seed → same board.
 */
public final class RandomBoards {

  private static final int[] NON_KING_PIECES = {
    Piece.WHITE_PAWN, Piece.WHITE_KNIGHT, Piece.WHITE_BISHOP, Piece.WHITE_ROOK, Piece.WHITE_QUEEN,
    Piece.BLACK_PAWN, Piece.BLACK_KNIGHT, Piece.BLACK_BISHOP, Piece.BLACK_ROOK, Piece.BLACK_QUEEN
  };

  private RandomBoards() {}

  public static Board create(long seed) {
    Random random = new Random(seed);
    Board board = new Board();

    board.putPiece(Piece.WHITE_KING, randomEmptySquare(board, random));
    board.putPiece(Piece.BLACK_KING, randomEmptySquare(board, random));

    int extraPieces = random.nextInt(31);
    for (int i = 0; i < extraPieces; i++) {
      int piece = NON_KING_PIECES[random.nextInt(NON_KING_PIECES.length)];
      int square = randomEmptySquare(board, random);
      int rank = Square.rank(square);
      if (Piece.type(piece) == Piece.PAWN && (rank == 0 || rank == 7)) {
        continue;
      }
      board.putPiece(piece, square);
    }

    board.setSideToMove(random.nextBoolean() ? Colour.WHITE : Colour.BLACK);
    board.setCastlingRights(random.nextInt(16));
    if (random.nextInt(4) == 0) {
      int rank = board.sideToMove() == Colour.WHITE ? 5 : 2;
      board.setEnPassantSquare(Square.of(random.nextInt(8), rank));
    }
    board.setHalfmoveClock(random.nextInt(100));
    board.setFullmoveNumber(1 + random.nextInt(200));
    return board;
  }

  private static int randomEmptySquare(Board board, Random random) {
    while (true) {
      int square = random.nextInt(Square.COUNT);
      if (board.pieceAt(square) == Piece.NONE) {
        return square;
      }
    }
  }
}
