package phase3;

import java.util.function.Consumer;

/**
 * Connects a running search with the outside world.
 *
 * <ul>
 *   <li>{@link #stop()} is called from another thread (the UCI {@code stop} command, or a time
 *       limit). The search checks {@link #isStopped()} regularly and returns its best move so far.
 *       The flag is {@code volatile} so the search thread sees the change immediately.
 *   <li>{@link #info(String)} sends progress lines ("info depth 5 score cp 30 ...") to the GUI.
 * </ul>
 */
public final class SearchContext {

  private volatile boolean stopped = false;
  private final Consumer<String> infoSink;

  public SearchContext(Consumer<String> infoSink) {
    this.infoSink = infoSink;
  }

  /** A context that throws away info lines (for tests and internal use). */
  public static SearchContext silent() {
    return new SearchContext(line -> {});
  }

  public void stop() {
    stopped = true;
  }

  public boolean isStopped() {
    return stopped;
  }

  public void info(String line) {
    infoSink.accept(line);
  }
}
