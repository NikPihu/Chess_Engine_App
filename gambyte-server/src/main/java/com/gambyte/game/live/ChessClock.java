package com.gambyte.game.live;

/**
 * A server-side chess clock. The server's clock is the only one that counts; the browser only
 * draws a countdown from the numbers in each update.
 *
 * <p><b>Lag compensation.</b> A move travels over the network before the server sees it, so the
 * player would lose that travel time on every move. The client reports its measured network lag
 * with each move; the server gives back up to {@link #MAX_LAG_COMPENSATION_MS} of it. The cap
 * keeps a cheater from claiming free time. (Lichess does something similar with a "lag quota".)
 *
 * <p>All times are epoch milliseconds. Not thread-safe: {@link LiveGame} guards it.
 */
public final class ChessClock {

  public static final long MAX_LAG_COMPENSATION_MS = 300;

  private final long initialMs;
  private final long incrementMs;
  private final long[] remainingMs = new long[2]; // [white, black]
  private int running = -1; // colour whose clock runs, -1 = stopped
  private long runningSince;

  public ChessClock(long initialMs, long incrementMs) {
    this.initialMs = initialMs;
    this.incrementMs = incrementMs;
    remainingMs[0] = initialMs;
    remainingMs[1] = initialMs;
  }

  /** Restores a clock from a snapshot (after a server restart). */
  public static ChessClock restore(
      long initialMs, long incrementMs, long whiteMs, long blackMs, int running, long since) {
    ChessClock c = new ChessClock(initialMs, incrementMs);
    c.remainingMs[0] = whiteMs;
    c.remainingMs[1] = blackMs;
    c.running = running;
    c.runningSince = since;
    return c;
  }

  /** "Unlimited" games against the computer have no clock at all. */
  public boolean isUntimed() {
    return initialMs == 0 && incrementMs == 0;
  }

  public void start(int colour, long now) {
    running = colour;
    runningSince = now;
  }

  public void stop(long now) {
    if (running >= 0) remainingMs[running] = remaining(running, now);
    running = -1;
  }

  /**
   * The mover pressed the clock: charge their thinking time (minus compensated lag), add the
   * increment and start the opponent's clock.
   *
   * @return the thinking time that was charged, in ms (stored for move-time statistics)
   */
  public long press(int colour, long now, long reportedLagMs) {
    long think = 0;
    if (running == colour) {
      long lag = Math.max(0, Math.min(reportedLagMs, MAX_LAG_COMPENSATION_MS));
      think = Math.max(0, now - runningSince - lag);
      remainingMs[colour] = remainingMs[colour] - think + incrementMs;
    }
    start(1 - colour, now);
    return think;
  }

  /** Time left for a colour at {@code now} (negative if their flag has fallen). */
  public long remaining(int colour, long now) {
    long left = remainingMs[colour];
    if (running == colour) left -= now - runningSince;
    return left;
  }

  /** The colour whose flag has fallen, or -1. */
  public int flagged(long now) {
    if (isUntimed() || running < 0) return -1;
    return remaining(running, now) <= 0 ? running : -1;
  }

  /** Milliseconds until the running clock reaches zero (for scheduling the flag check). */
  public long msUntilFlag(long now) {
    return running < 0 || isUntimed() ? Long.MAX_VALUE : Math.max(0, remaining(running, now));
  }

  public int running() {
    return running;
  }

  public long runningSince() {
    return runningSince;
  }

  public long initialMs() {
    return initialMs;
  }

  public long incrementMs() {
    return incrementMs;
  }

  /** Adds time to a player's clock ("give 15 seconds", or a takeback). */
  public void addTime(int colour, long ms) {
    remainingMs[colour] += ms;
  }
}
