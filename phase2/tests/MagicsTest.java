package phase2.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;
import phase2.Magics;
import phase2.RayAttacks;

/** The fast magic lookups must give exactly the same answer as the simple ray walk. */
class MagicsTest {

  @Test
  void magicsAgreeWithRayWalkingOnRandomBoards() {
    Random random = new Random(1);
    for (int i = 0; i < 20_000; i++) {
      // Sparse and dense boards both matter, so vary how many random bits we keep.
      long occupied = random.nextLong();
      for (int k = random.nextInt(4); k > 0; k--) {
        occupied &= random.nextLong();
      }
      int sq = random.nextInt(64);
      assertThat(Magics.rookAttacks(sq, occupied)).isEqualTo(RayAttacks.rook(sq, occupied));
      assertThat(Magics.bishopAttacks(sq, occupied)).isEqualTo(RayAttacks.bishop(sq, occupied));
    }
  }

  @Test
  void emptyAndFullBoards() {
    for (int sq = 0; sq < 64; sq++) {
      assertThat(Magics.rookAttacks(sq, 0)).isEqualTo(RayAttacks.rook(sq, 0));
      assertThat(Magics.bishopAttacks(sq, -1L)).isEqualTo(RayAttacks.bishop(sq, -1L));
      assertThat(Long.bitCount(Magics.rookAttacks(sq, 0))).isEqualTo(14);
    }
  }
}
