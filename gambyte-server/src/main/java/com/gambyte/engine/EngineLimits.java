package com.gambyte.engine;

/**
 * How long the engine may think. Exactly one style is used:
 *
 * <ul>
 *   <li>{@code nodes}: a fixed amount of work (same strength on any machine; used for levels,
 *       analysis and fair-play checks)
 *   <li>{@code moveTimeMs}: a fixed time per move
 *   <li>clock: the engine's own time management from the remaining time and increment, like a
 *       human with a chess clock (used by the full-strength level)
 * </ul>
 */
public record EngineLimits(
    long nodes, long moveTimeMs, long whiteMs, long blackMs, long whiteIncMs, long blackIncMs) {

  public static EngineLimits nodes(long nodes) {
    return new EngineLimits(nodes, 0, 0, 0, 0, 0);
  }

  public static EngineLimits moveTime(long ms) {
    return new EngineLimits(0, ms, 0, 0, 0, 0);
  }

  public static EngineLimits clock(long whiteMs, long blackMs, long whiteIncMs, long blackIncMs) {
    return new EngineLimits(0, 0, whiteMs, blackMs, whiteIncMs, blackIncMs);
  }

  /** The text after "go" in UCI. */
  public String toUci() {
    if (nodes > 0) return "nodes " + nodes;
    if (moveTimeMs > 0) return "movetime " + moveTimeMs;
    return "wtime %d btime %d winc %d binc %d"
        .formatted(Math.max(whiteMs, 1), Math.max(blackMs, 1), whiteIncMs, blackIncMs);
  }

  /** An upper bound on thinking time, used as a safety timeout. */
  public long maxTimeMs() {
    if (nodes > 0) return 5_000 + nodes / 50; // even a slow box does > 50 nodes per ms
    if (moveTimeMs > 0) return moveTimeMs + 5_000;
    return Math.max(whiteMs, blackMs) / 10 + 10_000;
  }
}
