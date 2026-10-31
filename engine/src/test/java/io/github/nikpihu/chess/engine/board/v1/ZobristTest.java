package io.github.nikpihu.chess.engine.board.v1;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nikpihu.chess.engine.testutil.RandomBoards;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ZobristTest {

  @Test
  void incrementalKeyAlwaysMatchesFullRecompute() {
    for (long seed = 0; seed < 1000; seed++) {
      Board board = RandomBoards.create(seed);
      assertThat(board.zobristKey()).as("seed %d", seed).isEqualTo(Zobrist.compute(board));
    }
  }

  @Test
  void keyStaysCorrectThroughManyEdits() {
    Board board = RandomBoards.create(7);
    Random random = new Random(7);
    for (int i = 0; i < 10_000; i++) {
      int sq = random.nextInt(Square.COUNT);
      switch (random.nextInt(5)) {
        case 0 -> board.removePiece(sq);
        case 1 -> {
          if (board.pieceAt(sq) == Piece.NONE) {
            board.putPiece(Piece.ALL[random.nextInt(12)], sq);
          }
        }
        case 2 -> board.setSideToMove(random.nextInt(2));
        case 3 -> board.setCastlingRights(random.nextInt(16));
        default ->
            board.setEnPassantSquare(
                random.nextBoolean() ? Square.NONE : Square.of(random.nextInt(8), 2));
      }
      assertThat(board.zobristKey()).isEqualTo(Zobrist.compute(board));
    }
  }

  @Test
  void orderOfPlacementDoesNotMatter() {
    Board a = new Board();
    a.putPiece(Piece.WHITE_KING, Square.E1);
    a.putPiece(Piece.BLACK_KING, Square.E8);
    Board b = new Board();
    b.putPiece(Piece.BLACK_KING, Square.E8);
    b.putPiece(Piece.WHITE_KING, Square.E1);
    assertThat(a.zobristKey()).isEqualTo(b.zobristKey());
  }

  @Test
  void sideToMoveChangesTheKey() {
    Board board = RandomBoards.create(3);
    long before = board.zobristKey();
    board.setSideToMove(Colour.opposite(board.sideToMove()));
    assertThat(board.zobristKey()).isNotEqualTo(before);
  }

  @Test
  void differentRandomPositionsGetDifferentKeys() {
    Set<Long> keys = new HashSet<>();
    for (long seed = 0; seed < 2000; seed++) {
      keys.add(RandomBoards.create(seed).zobristKey());
    }
    assertThat(keys).hasSize(2000);
  }
}
