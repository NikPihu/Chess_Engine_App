package io.github.nikpihu.chess.engine.board.v1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BitboardsTest {

  @Test
  void fileAndRankMasks() {
    assertThat(Bitboards.count(Bitboards.FILE_A)).isEqualTo(8);
    assertThat(Bitboards.contains(Bitboards.FILE_A, Square.A8)).isTrue();
    assertThat(Bitboards.contains(Bitboards.FILE_A, Square.B1)).isFalse();
    assertThat(Bitboards.FILES[7]).isEqualTo(Bitboards.FILE_H);
    assertThat(Bitboards.RANKS[7]).isEqualTo(Bitboards.RANK_8);
    assertThat(Bitboards.contains(Bitboards.RANK_8, Square.H8)).isTrue();
  }

  @Test
  void loopVisitsSquaresLowToHigh() {
    long bb = Bitboards.of(Square.E4) | Bitboards.of(Square.A1) | Bitboards.of(Square.H8);
    List<Integer> visited = new ArrayList<>();
    while (bb != 0) {
      visited.add(Bitboards.lowestSquare(bb));
      bb = Bitboards.withoutLowest(bb);
    }
    assertThat(visited).containsExactly(Square.A1, Square.E4, Square.H8);
  }

  @Test
  void highestBitIsHandled() {
    // h8 is bit 63, the sign bit of a long: a classic source of bugs with >> instead of >>>.
    long bb = Bitboards.of(Square.H8);
    assertThat(bb).isNegative();
    assertThat(Bitboards.contains(bb, Square.H8)).isTrue();
    assertThat(Bitboards.lowestSquare(bb)).isEqualTo(63);
  }
}
