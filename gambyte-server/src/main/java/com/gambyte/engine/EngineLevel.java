package com.gambyte.engine;

/**
 * The 8 strengths of the Gambyte Engine bot.
 *
 * <p>Weakening an engine convincingly is an art; this uses the two simplest levers:
 *
 * <ul>
 *   <li><b>nodes</b>: how much the engine may search (a tiny search misses tactics, like a beginner)
 *   <li><b>randomMoveChance</b>: how often it plays a random legal move instead (an outright
 *       blunder)
 * </ul>
 *
 * Level 8 has neither limit: the engine manages its own clock, at full strength. The ratings are
 * rough estimates for the UI and should be recalibrated from real games later.
 */
public enum EngineLevel {
  L1(1, 1, 0.55, 600),
  L2(2, 30, 0.30, 900),
  L3(3, 200, 0.16, 1200),
  L4(4, 1_500, 0.08, 1500),
  L5(5, 8_000, 0.04, 1800),
  L6(6, 50_000, 0.015, 2100),
  L7(7, 300_000, 0.0, 2400),
  L8(8, 0, 0.0, 2800);

  public final int number;
  public final long nodes;
  public final double randomMoveChance;
  public final int approxRating;

  EngineLevel(int number, long nodes, double randomMoveChance, int approxRating) {
    this.number = number;
    this.nodes = nodes;
    this.randomMoveChance = randomMoveChance;
    this.approxRating = approxRating;
  }

  public boolean fullStrength() {
    return nodes == 0;
  }

  public static EngineLevel of(int number) {
    if (number < 1 || number > 8) throw new IllegalArgumentException("level must be 1-8");
    return values()[number - 1];
  }
}
