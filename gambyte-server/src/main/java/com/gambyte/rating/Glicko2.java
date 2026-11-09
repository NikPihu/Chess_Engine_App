package com.gambyte.rating;

/**
 * The Glicko-2 rating system (Mark Glickman, http://www.glicko.net/glicko/glicko2.pdf), as used by
 * Lichess. Pure maths, no Spring, so it is easy to unit-test against the paper's worked example.
 *
 * <p>Compared with Elo, every player also has a <em>rating deviation</em> (RD): how sure we are of
 * the rating. New players start at 1500 ± 350 and move fast; established players move slowly. A
 * <em>volatility</em> tracks how erratic a player's results are.
 *
 * <p>We rate after every game (a "rating period" of one game), which is what online sites do.
 */
public final class Glicko2 {

  /** Constrains volatility changes; 0.3-1.2 is typical, Lichess uses 0.75. */
  private static final double TAU = 0.75;

  /** Converts between the Glicko scale (1500, 350) and the internal Glicko-2 scale. */
  private static final double SCALE = 173.7178;

  private static final double EPSILON = 0.000001;
  public static final double MIN_DEVIATION = 45;
  public static final double MAX_DEVIATION = 350;

  private Glicko2() {}

  /** A rating triple. */
  public record Rating(double rating, double deviation, double volatility) {}

  /**
   * The player's new rating after one game.
   *
   * @param player the player before the game
   * @param opponent the opponent before the game
   * @param score 1 = win, 0.5 = draw, 0 = loss
   */
  public static Rating update(Rating player, Rating opponent, double score) {
    double mu = (player.rating() - 1500) / SCALE;
    double phi = player.deviation() / SCALE;
    double sigma = player.volatility();
    double muJ = (opponent.rating() - 1500) / SCALE;
    double phiJ = opponent.deviation() / SCALE;

    double g = g(phiJ);
    double e = expected(mu, muJ, phiJ);
    double v = 1 / (g * g * e * (1 - e)); // estimated variance from this game
    double delta = v * g * (score - e); // estimated improvement

    double newSigma = newVolatility(phi, sigma, v, delta);
    double phiStar = Math.sqrt(phi * phi + newSigma * newSigma);
    double newPhi = 1 / Math.sqrt(1 / (phiStar * phiStar) + 1 / v);
    double newMu = mu + newPhi * newPhi * g * (score - e);

    double rating = newMu * SCALE + 1500;
    double deviation = Math.min(MAX_DEVIATION, Math.max(MIN_DEVIATION, newPhi * SCALE));
    return new Rating(rating, deviation, newSigma);
  }

  /** Expected score (0..1) of a player against an opponent, on the Glicko scale. */
  public static double expectedScore(Rating player, Rating opponent) {
    return expected(
        (player.rating() - 1500) / SCALE,
        (opponent.rating() - 1500) / SCALE,
        opponent.deviation() / SCALE);
  }

  private static double g(double phi) {
    return 1 / Math.sqrt(1 + 3 * phi * phi / (Math.PI * Math.PI));
  }

  private static double expected(double mu, double muJ, double phiJ) {
    return 1 / (1 + Math.exp(-g(phiJ) * (mu - muJ)));
  }

  /** Step 5 of the paper: solve for the new volatility with the Illinois algorithm. */
  private static double newVolatility(double phi, double sigma, double v, double delta) {
    double a = Math.log(sigma * sigma);
    double phi2 = phi * phi;
    double delta2 = delta * delta;
    java.util.function.DoubleUnaryOperator f =
        x -> {
          double ex = Math.exp(x);
          double d = phi2 + v + ex;
          return ex * (delta2 - phi2 - v - ex) / (2 * d * d) - (x - a) / (TAU * TAU);
        };
    double bigA = a;
    double bigB;
    if (delta2 > phi2 + v) {
      bigB = Math.log(delta2 - phi2 - v);
    } else {
      int k = 1;
      while (f.applyAsDouble(a - k * TAU) < 0) k++;
      bigB = a - k * TAU;
    }
    double fA = f.applyAsDouble(bigA);
    double fB = f.applyAsDouble(bigB);
    for (int i = 0; i < 100 && Math.abs(bigB - bigA) > EPSILON; i++) {
      double c = bigA + (bigA - bigB) * fA / (fB - fA);
      double fC = f.applyAsDouble(c);
      if (fC * fB <= 0) {
        bigA = bigB;
        fA = fB;
      } else {
        fA = fA / 2;
      }
      bigB = c;
      fB = fC;
    }
    return Math.exp(bigA / 2);
  }
}
