package uci;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
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
    ALL.put("v2", phase6.Search2::new);
    ALL.put("v1", phase4.Search::new);
    ALL.put("random", RandomPlayer::new);
  }

  /** Extra UCI options added by other engine modules: name -> (declaration line, handler). */
  private static final Map<String, CustomOption> OPTIONS = new LinkedHashMap<>();

  /** The engine used when none is chosen: the strongest one. */
  private static String defaultName = "v2";

  private Engines() {}

  /**
   * Adds an engine from another module (e.g. chess_engine_v2's NNUE engine). Registered engines
   * appear in the UCI "Engine" option and the board viewer's dropdown.
   */
  public static void register(String name, Supplier<Searcher> supplier) {
    ALL.put(name, supplier);
  }

  public static void setDefault(String name) {
    if (!ALL.containsKey(name)) {
      throw new IllegalArgumentException("Unknown engine: " + name);
    }
    defaultName = name;
  }

  public static String defaultName() {
    return defaultName;
  }

  /**
   * Adds a UCI option, e.g. {@code option name EvalFile type string default <none>}. The handler
   * receives the current engine and the value; it should ignore engines it doesn't apply to.
   */
  public static void registerOption(
      String name, String declaration, BiConsumer<Searcher, String> handler) {
    OPTIONS.put(name.toLowerCase(), new CustomOption(name, declaration, handler));
  }

  public static Iterable<CustomOption> customOptions() {
    return OPTIONS.values();
  }

  public static CustomOption customOption(String name) {
    return OPTIONS.get(name.toLowerCase());
  }

  /** An option added through {@link #registerOption}. */
  public record CustomOption(
      String name, String declaration, BiConsumer<Searcher, String> handler) {}

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
