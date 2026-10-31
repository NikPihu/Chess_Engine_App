package phase4;

import phase1.Colour;
import phase3.SearchLimits;

/**
 * Decides how long to think.
 *
 * <ul>
 *   <li>{@code go movetime X}: use X ms (minus a small safety margin for the GUI).
 *   <li>{@code go wtime/btime [winc/binc] [movestogo]}: assume about 30 moves are still to come, so
 *       spend {@code remaining / 30 + 3/4 of the increment}. That is the <b>soft</b> limit: after
 *       an iteration finishes past half of it, we don't start another one (the next depth would
 *       probably not finish anyway). The <b>hard</b> limit (3x soft, never more than half the
 *       clock) aborts the search in the middle of an iteration.
 *   <li>depth / nodes / infinite: no time limit.
 * </ul>
 */
public final class TimeManager {

  private static final long SAFETY_MS = 15;

  private final long softMs;
  private final long hardMs;
  private final boolean clock;

  public TimeManager(SearchLimits limits, int sideToMove) {
    if (limits.moveTimeMs > 0) {
      softMs = hardMs = Math.max(1, limits.moveTimeMs - SAFETY_MS);
      clock = false;
    } else if (limits.hasClock() && !limits.infinite) {
      long remaining = sideToMove == Colour.WHITE ? limits.whiteTimeMs : limits.blackTimeMs;
      long increment =
          sideToMove == Colour.WHITE ? limits.whiteIncrementMs : limits.blackIncrementMs;
      int movesLeft = limits.movesToGo > 0 ? limits.movesToGo : 30;
      long usable = Math.max(1, remaining - 3 * SAFETY_MS);
      long soft = remaining / movesLeft + increment * 3 / 4;
      softMs = Math.max(1, Math.min(soft, usable));
      hardMs = Math.max(1, Math.min(softMs * 3, Math.max(1, remaining / 2)));
      clock = true;
    } else {
      softMs = hardMs = -1;
      clock = false;
    }
  }

  /** Milliseconds after which the search must abort, or -1 for no limit. */
  public long hardLimitMs() {
    return hardMs;
  }

  /** After a finished iteration: is it pointless to start the next one? */
  public boolean stopAfterIteration(long elapsedMs) {
    return clock && elapsedMs >= softMs / 2;
  }
}
