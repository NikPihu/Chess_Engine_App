package com.gambyte.rating;

import com.gambyte.user.domain.Perf;
import com.gambyte.user.domain.Rating;
import com.gambyte.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies Glicko-2 to finished rated games and stores the new ratings. */
@Service
public class RatingService {

  private final UserService users;

  public RatingService(UserService users) {
    this.users = users;
  }

  /** Rating changes of one game, already rounded for display (e.g. +8 / -8). */
  public record Diffs(int white, int black) {}

  /**
   * Updates both players' ratings.
   *
   * @param whiteScore 1 = white won, 0.5 = draw, 0 = black won
   */
  @Transactional
  public Diffs apply(long whiteId, long blackId, Perf perf, double whiteScore) {
    Rating white = users.ratingFor(whiteId, perf);
    Rating black = users.ratingFor(blackId, perf);
    Glicko2.Rating w = triple(white);
    Glicko2.Rating b = triple(black);
    Glicko2.Rating newW = Glicko2.update(w, b, whiteScore);
    Glicko2.Rating newB = Glicko2.update(b, w, 1 - whiteScore);
    white.update(newW.rating(), newW.deviation(), newW.volatility());
    black.update(newB.rating(), newB.deviation(), newB.volatility());
    users.saveRating(white);
    users.saveRating(black);
    return new Diffs(
        (int) Math.round(newW.rating()) - (int) Math.round(w.rating()),
        (int) Math.round(newB.rating()) - (int) Math.round(b.rating()));
  }

  /** Current rating rounded for display, e.g. on the game screen. */
  public int current(long userId, Perf perf) {
    return (int) Math.round(users.ratingFor(userId, perf).getRating());
  }

  public boolean provisional(long userId, Perf perf) {
    return users.ratingFor(userId, perf).isProvisional();
  }

  private static Glicko2.Rating triple(Rating r) {
    return new Glicko2.Rating(r.getRating(), r.getDeviation(), r.getVolatility());
  }
}
