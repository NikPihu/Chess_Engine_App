package com.gambyte.fairplay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FairPlayStatsTest {

  /** Human-like thinking times: quick and slow moves mixed. */
  private static final List<Integer> HUMAN_TIMES =
      List.of(800, 4200, 1500, 12000, 900, 3000, 25000, 1100, 6000, 2000, 700, 9000, 1600, 4000, 15000);

  @Test
  void anEngineLikeGameIsFlagged() {
    // 30 moves, 29 engine matches, ACPL 4, metronome-like timing, at 1400 rating.
    List<Integer> even = Collections.nCopies(30, 3000);
    var s = FairPlayService.stats(30, 29, 30 * 4, even, 6, 1400);
    assertThat(s.suspicion()).isGreaterThanOrEqualTo(0.7);
  }

  @Test
  void aNormalHumanGameIsNotFlagged() {
    var s = FairPlayService.stats(30, 15, 30 * 60, HUMAN_TIMES, 0, 1400);
    assertThat(s.suspicion()).isLessThan(0.4);
  }

  @Test
  void strongPlayersAreHeldToAHigherStandard() {
    // The same accuracy is far less surprising at 2500 than at 1200.
    var low = FairPlayService.stats(30, 22, 30 * 18, HUMAN_TIMES, 0, 1200);
    var high = FairPlayService.stats(30, 22, 30 * 18, HUMAN_TIMES, 0, 2500);
    assertThat(low.suspicion()).isGreaterThan(high.suspicion());
  }

  @Test
  void fewMovesGiveNoRhythmSignal() {
    var s = FairPlayService.stats(5, 5, 0, IntStream.range(0, 5).map(i -> 3000).boxed().toList(), 0, 1500);
    assertThat(s.timeCv()).isEqualTo(0.0);
  }
}
