package com.gambyte.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.gambyte.game.live.ChessClock;
import org.junit.jupiter.api.Test;

class ChessClockTest {

  @Test
  void pressingChargesThinkingTimeAndAddsIncrement() {
    ChessClock c = new ChessClock(60_000, 2_000);
    c.start(0, 1_000);
    long think = c.press(0, 6_000, 0); // white thought 5 s
    assertThat(think).isEqualTo(5_000);
    assertThat(c.remaining(0, 6_000)).isEqualTo(60_000 - 5_000 + 2_000);
    assertThat(c.running()).isEqualTo(1);
  }

  @Test
  void lagCompensationIsCapped() {
    ChessClock c = new ChessClock(60_000, 0);
    c.start(0, 0);
    c.press(0, 1_000, 10_000); // claims 10 s of lag: only 300 ms is credited
    assertThat(c.remaining(0, 1_000)).isEqualTo(60_000 - (1_000 - ChessClock.MAX_LAG_COMPENSATION_MS));
  }

  @Test
  void theFlagFallsAtZero() {
    ChessClock c = new ChessClock(10_000, 0);
    c.start(1, 0);
    assertThat(c.flagged(9_999)).isEqualTo(-1);
    assertThat(c.flagged(10_000)).isEqualTo(1);
  }

  @Test
  void untimedGamesNeverFlag() {
    ChessClock c = new ChessClock(0, 0);
    c.start(0, 0);
    assertThat(c.isUntimed()).isTrue();
    assertThat(c.flagged(Long.MAX_VALUE / 2)).isEqualTo(-1);
  }

  @Test
  void restoreKeepsRemainingTimes() {
    ChessClock c = ChessClock.restore(60_000, 0, 30_000, 45_000, 1, 0);
    assertThat(c.remaining(0, 5_000)).isEqualTo(30_000);
    assertThat(c.remaining(1, 5_000)).isEqualTo(40_000);
  }
}
