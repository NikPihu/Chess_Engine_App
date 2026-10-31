package io.github.nikpihu.chess.engine.board.v1;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PieceTest {

  @Test
  void encodingMatchesTheDocumentedLayout() {
    assertThat(Piece.WHITE_PAWN).isEqualTo(1);
    assertThat(Piece.WHITE_KING).isEqualTo(6);
    assertThat(Piece.BLACK_PAWN).isEqualTo(9);
    assertThat(Piece.BLACK_KING).isEqualTo(14);
  }

  @Test
  void colourAndTypeAreExtracted() {
    for (int piece : Piece.ALL) {
      assertThat(Piece.make(Piece.colour(piece), Piece.type(piece))).isEqualTo(piece);
      assertThat(Piece.isValid(piece)).isTrue();
    }
    assertThat(Piece.colour(Piece.BLACK_QUEEN)).isEqualTo(Colour.BLACK);
    assertThat(Piece.type(Piece.BLACK_QUEEN)).isEqualTo(Piece.QUEEN);
  }

  @Test
  void fenLettersRoundTrip() {
    for (char c : "PNBRQKpnbrqk".toCharArray()) {
      assertThat(Piece.toChar(Piece.fromChar(c))).isEqualTo(c);
    }
    assertThat(Piece.fromChar('x')).isEqualTo(Piece.NONE);
    assertThat(Piece.fromChar('.')).isEqualTo(Piece.NONE);
    assertThat(Piece.toChar(Piece.NONE)).isEqualTo('.');
  }

  @Test
  void oppositeColour() {
    assertThat(Colour.opposite(Colour.WHITE)).isEqualTo(Colour.BLACK);
    assertThat(Colour.opposite(Colour.BLACK)).isEqualTo(Colour.WHITE);
  }
}
