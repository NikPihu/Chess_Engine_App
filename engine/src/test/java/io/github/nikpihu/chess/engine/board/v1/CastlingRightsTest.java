package io.github.nikpihu.chess.engine.board.v1;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CastlingRightsTest {

  @Test
  void allSixteenCombinationsRoundTrip() {
    for (int rights = 0; rights < 16; rights++) {
      assertThat(CastlingRights.fromFen(CastlingRights.toFen(rights))).isEqualTo(rights);
    }
  }

  @Test
  void knownValues() {
    assertThat(CastlingRights.toFen(CastlingRights.ALL)).isEqualTo("KQkq");
    assertThat(CastlingRights.toFen(CastlingRights.NONE)).isEqualTo("-");
    assertThat(CastlingRights.fromFen("Kq"))
        .isEqualTo(CastlingRights.WHITE_KING_SIDE | CastlingRights.BLACK_QUEEN_SIDE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "KK", "X", "KQkqK", "-K"})
  void invalidTextIsRejected(String text) {
    assertThat(CastlingRights.fromFen(text)).isEqualTo(-1);
  }
}
