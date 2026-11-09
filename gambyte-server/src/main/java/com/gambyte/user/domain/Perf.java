package com.gambyte.user.domain;

import java.util.Arrays;

/**
 * A rating pool. Games are put in a pool by their estimated duration, exactly like Lichess:
 * {@code initial seconds + 40 × increment seconds}.
 *
 * <ul>
 *   <li>bullet: under 3 minutes (1+0, 2+1)
 *   <li>blitz: under 8 minutes (3+0, 3+2, 5+0, 5+3)
 *   <li>rapid: under 25 minutes (10+0, 15+10)
 *   <li>classical: 25 minutes and more (30+0, 30+20)
 * </ul>
 */
public enum Perf {
  BULLET("bullet"),
  BLITZ("blitz"),
  RAPID("rapid"),
  CLASSICAL("classical"),
  PUZZLE("puzzle");

  private final String key;

  Perf(String key) {
    this.key = key;
  }

  /** The lower-case name stored in the database and used in URLs. */
  public String key() {
    return key;
  }

  public static Perf fromKey(String key) {
    return Arrays.stream(values())
        .filter(p -> p.key.equals(key))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("unknown perf " + key));
  }

  /** The pool for a time control. */
  public static Perf forTimeControl(int initialSeconds, int incrementSeconds) {
    int estimate = initialSeconds + 40 * incrementSeconds;
    if (estimate < 180) return BULLET;
    if (estimate < 480) return BLITZ;
    if (estimate < 1500) return RAPID;
    return CLASSICAL;
  }
}
