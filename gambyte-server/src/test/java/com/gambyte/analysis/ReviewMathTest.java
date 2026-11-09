package com.gambyte.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewMathTest {

  @Test
  void winChanceIsFiftyAtZeroAndSymmetric() {
    assertThat(ReviewService.winChance(0)).isCloseTo(50, within(1e-9));
    assertThat(ReviewService.winChance(300) + ReviewService.winChance(-300)).isCloseTo(100, within(1e-9));
    assertThat(ReviewService.winChance(10_000)).isGreaterThan(99.9);
  }

  @Test
  void classificationThresholds() {
    assertThat(ReviewService.classify(0, true)).isEqualTo("best");
    assertThat(ReviewService.classify(1, false)).isEqualTo("excellent");
    assertThat(ReviewService.classify(5, false)).isEqualTo("good");
    assertThat(ReviewService.classify(12, false)).isEqualTo("inaccuracy");
    assertThat(ReviewService.classify(25, false)).isEqualTo("mistake");
    assertThat(ReviewService.classify(40, true)).isEqualTo("blunder");
  }

  @Test
  void coachSeesHangingMaterialInTheReplyLine() {
    // Black played ...Qd8-h4?? and White's best reply Bxh4... simpler: White queen on d1 can take
    // an undefended rook on d8: after the move, material swings by 5 for the side that captures.
    int swing = CoachExplainer.materialSwing("3r2k1/8/8/8/8/8/8/3Q2K1 w - - 0 1", List.of("d1d8"), 4, phase1.Colour.WHITE);
    assertThat(swing).isEqualTo(5);
  }

  @Test
  void coachExplainsAMissedMate() {
    String text = CoachExplainer.explain("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", "g1f1",
        List.of("d1d8"), List.of(), null, 1, 40);
    assertThat(text).contains("mate in 1").contains("Rd8");
  }
}
