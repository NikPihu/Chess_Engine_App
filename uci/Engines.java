package uci;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import phase3.RandomPlayer;
import phase3.Searcher;

/**
 * Every engine version the program can play with, by name. Each phase that adds a stronger search
 * registers it here; older versions stay available so they can be tested against each other ({@code
 * setoption name Engine value random}).
 */
public final class Engines {

  private static final Map<String, Supplier<Searcher>> ALL = new LinkedHashMap<>();

  static {
    ALL.put("v1", phase4.Search::new);
    ALL.put("random", RandomPlayer::new);
  }

  /** The engine used when none is chosen: the strongest one. */
  public static final String DEFAULT = "v1";

  private Engines() {}

  public static Iterable<String> names() {
    return ALL.keySet();
  }

  public static boolean exists(String name) {
    return ALL.containsKey(name);
  }

  public static Searcher create(String name) {
    Supplier<Searcher> supplier = ALL.get(name);
    if (supplier == null) {
      throw new IllegalArgumentException("Unknown engine: " + name);
    }
    return supplier.get();
  }
}
