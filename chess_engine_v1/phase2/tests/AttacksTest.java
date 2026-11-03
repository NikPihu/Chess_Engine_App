package phase2.tests;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import phase1.Bitboards;
import phase1.Colour;
import phase1.Square;
import phase2.Attacks;
import phase2.Lines;

class AttacksTest {

  @Test
  void knightCountsDependOnTheSquare() {
    assertThat(Bitboards.count(Attacks.KNIGHT[Square.A1])).isEqualTo(2);
    assertThat(Bitboards.count(Attacks.KNIGHT[Square.B1])).isEqualTo(3);
    assertThat(Bitboards.count(Attacks.KNIGHT[Square.E4])).isEqualTo(8);
    assertThat(Bitboards.contains(Attacks.KNIGHT[Square.G1], Square.F3)).isTrue();
  }

  @Test
  void kingAndPawnAttacks() {
    assertThat(Bitboards.count(Attacks.KING[Square.H8])).isEqualTo(3);
    assertThat(Bitboards.count(Attacks.KING[Square.D4])).isEqualTo(8);
    assertThat(Attacks.PAWN[Colour.WHITE][Square.E4])
        .isEqualTo(Bitboards.of(Square.D5) | Bitboards.of(Square.F5));
    assertThat(Attacks.PAWN[Colour.BLACK][Square.A5]).isEqualTo(Bitboards.of(Square.B4));
  }

  @Test
  void betweenAndLine() {
    assertThat(Lines.BETWEEN[Square.E1][Square.E4])
        .isEqualTo(Bitboards.of(Square.E2) | Bitboards.of(Square.E3));
    assertThat(Lines.BETWEEN[Square.A1][Square.D4])
        .isEqualTo(Bitboards.of(Square.B2) | Bitboards.of(Square.C3));
    assertThat(Lines.BETWEEN[Square.A1][Square.B3]).isZero(); // not aligned
    assertThat(Lines.LINE[Square.E1][Square.E4]).isEqualTo(Bitboards.FILES[4]);
    assertThat(Bitboards.count(Lines.LINE[Square.A1][Square.C3])).isEqualTo(8);
  }
}
