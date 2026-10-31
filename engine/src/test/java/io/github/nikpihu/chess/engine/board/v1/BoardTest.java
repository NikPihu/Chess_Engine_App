package io.github.nikpihu.chess.engine.board.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nikpihu.chess.engine.testutil.RandomBoards;
import org.junit.jupiter.api.Test;

class BoardTest {

  @Test
  void putPieceUpdatesEveryView() {
    Board board = new Board();
    board.putPiece(Piece.WHITE_KNIGHT, Square.G1);

    assertThat(board.pieceAt(Square.G1)).isEqualTo(Piece.WHITE_KNIGHT);
    assertThat(board.pieces(Piece.WHITE_KNIGHT)).isEqualTo(Bitboards.of(Square.G1));
    assertThat(board.piecesOf(Colour.WHITE)).isEqualTo(Bitboards.of(Square.G1));
    assertThat(board.piecesOf(Colour.BLACK)).isZero();
    assertThat(board.occupied()).isEqualTo(Bitboards.of(Square.G1));
  }

  @Test
  void removePieceClearsEveryViewAndRestoresHash() {
    Board board = new Board();
    long emptyKey = board.zobristKey();
    board.putPiece(Piece.BLACK_QUEEN, Square.D8);

    assertThat(board.removePiece(Square.D8)).isEqualTo(Piece.BLACK_QUEEN);
    assertThat(board.pieceAt(Square.D8)).isEqualTo(Piece.NONE);
    assertThat(board.pieces(Piece.BLACK_QUEEN)).isZero();
    assertThat(board.occupied()).isZero();
    assertThat(board.zobristKey()).isEqualTo(emptyKey);
    assertThat(board.removePiece(Square.D8)).isEqualTo(Piece.NONE);
  }

  @Test
  void cannotPutOnOccupiedSquare() {
    Board board = new Board();
    board.putPiece(Piece.WHITE_PAWN, Square.E2);
    assertThatThrownBy(() -> board.putPiece(Piece.BLACK_PAWN, Square.E2))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("e2");
  }

  @Test
  void bitboardsAndMailboxAgreeOnRandomBoards() {
    for (long seed = 0; seed < 500; seed++) {
      Board board = RandomBoards.create(seed);
      long all = 0;
      for (int piece : Piece.ALL) {
        long bb = board.pieces(piece);
        for (int sq = 0; sq < Square.COUNT; sq++) {
          assertThat(Bitboards.contains(bb, sq)).isEqualTo(board.pieceAt(sq) == piece);
        }
        all |= bb;
      }
      assertThat(board.occupied()).isEqualTo(all);
      assertThat(board.piecesOf(Colour.WHITE) | board.piecesOf(Colour.BLACK)).isEqualTo(all);
      assertThat(board.piecesOf(Colour.WHITE) & board.piecesOf(Colour.BLACK)).isZero();
    }
  }

  @Test
  void kingSquare() {
    Board board = new Board();
    assertThat(board.kingSquare(Colour.WHITE)).isEqualTo(Square.NONE);
    board.putPiece(Piece.WHITE_KING, Square.E1);
    assertThat(board.kingSquare(Colour.WHITE)).isEqualTo(Square.E1);
  }

  @Test
  void copyIsIndependent() {
    Board original = RandomBoards.create(42);
    Board copy = original.copy();
    assertThat(copy).isEqualTo(original);

    int sq = Bitboards.lowestSquare(copy.occupied());
    copy.removePiece(sq);
    copy.setSideToMove(Colour.opposite(copy.sideToMove()));

    assertThat(copy).isNotEqualTo(original);
    assertThat(original.pieceAt(sq)).isNotEqualTo(Piece.NONE);
  }
}
