package phase1.tests;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import phase1.Move;
import phase1.MoveFlag;
import phase1.Piece;
import phase1.Square;

class MoveTest {

  @Test
  void bitLayoutIsFlagTargetStart() {
    int move = Move.of(Square.E2, Square.E4, MoveFlag.PAWN_DOUBLE_PUSH);
    // 0011 011100 001100  = flag 3, target 28 (e4), start 12 (e2)
    assertThat(move).isEqualTo(0b0011_011100_001100);
    assertThat(Move.start(move)).isEqualTo(Square.E2);
    assertThat(Move.target(move)).isEqualTo(Square.E4);
    assertThat(Move.flag(move)).isEqualTo(MoveFlag.PAWN_DOUBLE_PUSH);
  }

  @Test
  void everyStartTargetFlagCombinationRoundTrips() {
    for (int start = 0; start < 64; start++) {
      for (int target = 0; target < 64; target++) {
        for (int flag = 0; flag < 8; flag++) {
          int move = Move.of(start, target, flag);
          assertThat(move).isBetween(0, 0xFFFF);
          assertThat(Move.start(move)).isEqualTo(start);
          assertThat(Move.target(move)).isEqualTo(target);
          assertThat(Move.flag(move)).isEqualTo(flag);
        }
      }
    }
  }

  @Test
  void uciText() {
    assertThat(Move.toUci(Move.of(Square.G1, Square.F3))).isEqualTo("g1f3");
    assertThat(Move.toUci(Move.of(Square.E7, Square.E8, MoveFlag.PROMOTE_QUEEN)))
        .isEqualTo("e7e8q");
    assertThat(Move.toUci(Move.of(Square.A2, Square.A1, MoveFlag.PROMOTE_KNIGHT)))
        .isEqualTo("a2a1n");
    assertThat(Move.toUci(Move.NULL)).isEqualTo("0000");
  }

  @Test
  void promotionFlags() {
    for (int flag = 0; flag < 8; flag++) {
      assertThat(MoveFlag.isPromotion(flag)).isEqualTo(flag >= 4);
    }
    for (int type : new int[] {Piece.QUEEN, Piece.KNIGHT, Piece.ROOK, Piece.BISHOP}) {
      assertThat(MoveFlag.promotionType(MoveFlag.promotionFlag(type))).isEqualTo(type);
    }
  }
}
