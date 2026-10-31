package phase1.tests;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import phase1.Square;

class SquareTest {

  @ParameterizedTest
  @CsvSource({"a1, 0", "h1, 7", "a2, 8", "e4, 28", "d5, 35", "a8, 56", "h8, 63"})
  void namesAndIndexesMatch(String name, int index) {
    assertThat(Square.parse(name)).isEqualTo(index);
    assertThat(Square.name(index)).isEqualTo(name);
  }

  @Test
  void constantsFollowTheLayout() {
    assertThat(Square.E4).isEqualTo(Square.of(4, 3));
    assertThat(Square.file(Square.E4)).isEqualTo(4);
    assertThat(Square.rank(Square.E4)).isEqualTo(3);
  }

  @Test
  void everySquareRoundTrips() {
    for (int sq = 0; sq < Square.COUNT; sq++) {
      assertThat(Square.of(Square.file(sq), Square.rank(sq))).isEqualTo(sq);
      assertThat(Square.parse(Square.name(sq))).isEqualTo(sq);
    }
  }

  @Test
  void badNamesGiveNone() {
    assertThat(Square.parse("i1")).isEqualTo(Square.NONE);
    assertThat(Square.parse("a9")).isEqualTo(Square.NONE);
    assertThat(Square.parse("e")).isEqualTo(Square.NONE);
    assertThat(Square.parse(null)).isEqualTo(Square.NONE);
    assertThat(Square.name(Square.NONE)).isEqualTo("-");
  }
}
