package com.gambyte.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/**
 * One Glicko-2 rating of one user in one {@link Perf} (rating pool).
 *
 * <p>Glicko-2 keeps three numbers: the rating, its deviation (how unsure we are; a new player
 * starts at 350 and it shrinks with games) and the volatility (how erratic results are). The maths
 * lives in {@code rating.Glicko2}; this class only stores the numbers.
 */
@Entity
@Table(name = "ratings")
public class Rating {

  /** A rating is "provisional" (shown with a ?) while the deviation is above this. */
  public static final double PROVISIONAL_DEVIATION = 110;

  @EmbeddedId private Key id;

  private double rating = 1500;
  private double deviation = 350;
  private double volatility = 0.06;
  private int games;
  private int best = 1500;

  @Column(nullable = false)
  private Instant updatedAt = Instant.now();

  protected Rating() {}

  public Rating(long userId, Perf perf) {
    this.id = new Key(userId, perf.key());
  }

  public long userId() {
    return id.userId;
  }

  public Perf perf() {
    return Perf.fromKey(id.perf);
  }

  public boolean isProvisional() {
    return deviation > PROVISIONAL_DEVIATION;
  }

  /** Stores the result of a rating update. */
  public void update(double rating, double deviation, double volatility) {
    this.rating = rating;
    this.deviation = deviation;
    this.volatility = volatility;
    this.games++;
    this.best = Math.max(best, (int) Math.round(rating));
    this.updatedAt = Instant.now();
  }

  public double getRating() {
    return rating;
  }

  public double getDeviation() {
    return deviation;
  }

  public double getVolatility() {
    return volatility;
  }

  public int getGames() {
    return games;
  }

  public int getBest() {
    return best;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /** Composite primary key (user_id, perf). */
  @Embeddable
  public static class Key implements Serializable {
    @Column(name = "user_id")
    private long userId;

    private String perf;

    protected Key() {}

    public Key(long userId, String perf) {
      this.userId = userId;
      this.perf = perf;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Key k && k.userId == userId && k.perf.equals(perf);
    }

    @Override
    public int hashCode() {
      return Long.hashCode(userId) * 31 + perf.hashCode();
    }
  }
}
