package io.github.nikpihu.chess.engine.fen.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nikpihu.chess.engine.board.v1.Bitboards;
import io.github.nikpihu.chess.engine.board.v1.Board;
import io.github.nikpihu.chess.engine.board.v1.CastlingRights;
import io.github.nikpihu.chess.engine.board.v1.Colour;
import io.github.nikpihu.chess.engine.board.v1.Piece;
import io.github.nikpihu.chess.engine.board.v1.Square;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FenParserTest {

  @Test
  void startPosition() {
    Board board = FenParser.parse(FenParser.START_POSITION);

    assertThat(board.pieceAt(Square.E1)).isEqualTo(Piece.WHITE_KING);
    assertThat(board.pieceAt(Square.D8)).isEqualTo(Piece.BLACK_QUEEN);
    assertThat(board.pieceAt(Square.E4)).isEqualTo(Piece.NONE);
    assertThat(board.pieces(Piece.WHITE_PAWN)).isEqualTo(Bitboards.RANKS[1]);
    assertThat(board.pieces(Piece.BLACK_PAWN)).isEqualTo(Bitboards.RANKS[6]);
    assertThat(Bitboards.count(board.occupied())).isEqualTo(32);
    assertThat(board.sideToMove()).isEqualTo(Colour.WHITE);
    assertThat(board.castlingRights()).isEqualTo(CastlingRights.ALL);
    assertThat(board.enPassantSquare()).isEqualTo(Square.NONE);
    assertThat(board.halfmoveClock()).isZero();
    assertThat(board.fullmoveNumber()).isEqualTo(1);
  }

  @Test
  void enPassantAndBlackToMove() {
    Board board = FenParser.parse("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");
    assertThat(board.sideToMove()).isEqualTo(Colour.BLACK);
    assertThat(board.enPassantSquare()).isEqualTo(Square.E3);
  }

  @ParameterizedTest(name = "{1}")
  @CsvSource(
      delimiter = '|',
      value = {
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -  0          | expected 4 or 6 fields",
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1                  | expected 8 ranks",
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNRR w KQkq - 0 1        | more than 8 squares",
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBN w KQkq - 0 1          | has 7 squares",
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQXBNR w KQkq - 0 1         | unknown piece letter 'X'",
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQ1BNR w KQkq - 0 1         | white has 0 kings",
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBKKBNR w KQkq - 0 1         | white has 2 kings",
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNP w KQkq - 0 1         | pawn on rank 1",
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1         | side to move",
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkx - 0 1         | bad castling field",
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e9 0 1        | bad en-passant square",
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e3 0 1        | is impossible with white",
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - x 1         | halfmove clock is not a number",
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 0         | fullmove number must be at least 1",
      })
  void invalidFenIsRejectedWithAClearMessage(String fen, String expectedMessage) {
    assertThatThrownBy(() -> FenParser.parse(fen))
        .isInstanceOf(FenException.class)
        .hasMessageContaining(expectedMessage.trim());
  }
}
