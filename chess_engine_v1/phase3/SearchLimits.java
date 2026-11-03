package phase3;

/**
 * What the GUI asked for with the UCI {@code go} command: how long or how deep to think.
 *
 * <pre>
 *   go depth 6                      search exactly 6 plies deep
 *   go movetime 1000                think for 1 second
 *   go wtime 60000 btime 60000 winc 1000 binc 1000   play with a clock (ms)
 *   go nodes 100000                 stop after this many positions
 *   go infinite                     until "stop"
 * </pre>
 *
 * Unset values are -1.
 */
public final class SearchLimits {

  public int depth = -1;
  public long moveTimeMs = -1;
  public long whiteTimeMs = -1;
  public long blackTimeMs = -1;
  public long whiteIncrementMs = 0;
  public long blackIncrementMs = 0;
  public int movesToGo = -1;
  public long nodes = -1;
  public boolean infinite = false;

  public static SearchLimits depth(int depth) {
    SearchLimits limits = new SearchLimits();
    limits.depth = depth;
    return limits;
  }

  public static SearchLimits moveTime(long ms) {
    SearchLimits limits = new SearchLimits();
    limits.moveTimeMs = ms;
    return limits;
  }

  /** Parses the words after {@code go}. Unknown words are ignored, as the UCI spec asks. */
  public static SearchLimits parse(String[] tokens, int start) {
    SearchLimits limits = new SearchLimits();
    for (int i = start; i < tokens.length; i++) {
      String value = i + 1 < tokens.length ? tokens[i + 1] : "0";
      switch (tokens[i]) {
        case "depth" -> limits.depth = Integer.parseInt(value);
        case "movetime" -> limits.moveTimeMs = Long.parseLong(value);
        case "wtime" -> limits.whiteTimeMs = Long.parseLong(value);
        case "btime" -> limits.blackTimeMs = Long.parseLong(value);
        case "winc" -> limits.whiteIncrementMs = Long.parseLong(value);
        case "binc" -> limits.blackIncrementMs = Long.parseLong(value);
        case "movestogo" -> limits.movesToGo = Integer.parseInt(value);
        case "nodes" -> limits.nodes = Long.parseLong(value);
        case "infinite" -> limits.infinite = true;
        default -> {
          continue;
        }
      }
      if (!tokens[i].equals("infinite")) {
        i++;
      }
    }
    return limits;
  }

  /** True when there is a clock to manage (wtime/btime given). */
  public boolean hasClock() {
    return whiteTimeMs >= 0 || blackTimeMs >= 0;
  }
}
