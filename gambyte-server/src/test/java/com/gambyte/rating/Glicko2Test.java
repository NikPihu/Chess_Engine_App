package com.gambyte.rating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.gambyte.rating.Glicko2.Rating;
import org.junit.jupiter.api.Test;

class Glicko2Test {

  private static final Rating NEW = new Rating(1500, 350, 0.06);

  @Test
  void winningRaisesAndLosingLowersTheRating() {
    Rating win = Glicko2.update(NEW, NEW, 1);
    Rating loss = Glicko2.update(NEW, NEW, 0);
    assertThat(win.rating()).isGreaterThan(1500);
    assertThat(loss.rating()).isLessThan(1500);
    // Two new players: the changes mirror each other.
    assertThat(win.rating() - 1500).isCloseTo(1500 - loss.rating(), within(0.001));
  }

  @Test
  void aDrawBetweenEqualPlayersChangesNothingButCertainty() {
    Rating draw = Glicko2.update(NEW, NEW, 0.5);
    assertThat(draw.rating()).isCloseTo(1500, within(0.001));
    assertThat(draw.deviation()).isLessThan(350);
  }

  @Test
  void establishedPlayersMoveLessThanNewOnes() {
    Rating established = new Rating(1500, 60, 0.06);
    double newGain = Glicko2.update(NEW, NEW, 1).rating() - 1500;
    double establishedGain = Glicko2.update(established, NEW, 1).rating() - 1500;
    assertThat(establishedGain).isLessThan(newGain / 3);
  }

  @Test
  void beatingAStrongerPlayerIsWorthMore() {
    Rating strong = new Rating(1900, 100, 0.06);
    Rating weak = new Rating(1100, 100, 0.06);
    Rating me = new Rating(1500, 100, 0.06);
    assertThat(Glicko2.update(me, strong, 1).rating()).isGreaterThan(Glicko2.update(me, weak, 1).rating());
  }

  @Test
  void deviationStaysWithinBounds() {
    Rating r = NEW;
    for (int i = 0; i < 500; i++) r = Glicko2.update(r, NEW, i % 2);
    assertThat(r.deviation()).isBetween(Glicko2.MIN_DEVIATION, Glicko2.MAX_DEVIATION);
  }

  @Test
  void expectedScoreIsHalfForEqualPlayersAndHigherForTheStronger() {
    assertThat(Glicko2.expectedScore(NEW, NEW)).isCloseTo(0.5, within(1e-9));
    assertThat(Glicko2.expectedScore(new Rating(1800, 50, 0.06), new Rating(1500, 50, 0.06))).isGreaterThan(0.8);
  }
}
