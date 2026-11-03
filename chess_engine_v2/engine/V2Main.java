package engine;

import java.io.IOException;
import java.nio.file.Path;
import nnue.NetFile;
import nnue.Network;
import search.NnueSearch;
import uci.Engines;
import uci.UciMain;

/**
 * Entry point of {@code chess_engine_v2.jar}.
 *
 * <p>Adds the {@code nnue} engine (and the UCI option {@code EvalFile} to choose its network) to
 * chess_engine_v1's engine list, then starts v1's UCI program, so every command you know works the
 * same ({@code d}, {@code go}, {@code gui}, ...) and the older engines ({@code v2}, {@code v1},
 * {@code random}) stay available for comparisons.
 *
 * <p>{@code nnue} becomes the default engine when a network is bundled in the jar ({@code
 * nets/best.nnue}); otherwise the default stays v1's best search.
 */
public final class V2Main {

  private V2Main() {}

  public static void main(String[] args) throws IOException {
    registerEngines();
    UciMain.main(args);
  }

  /** Public so tests and tools (datagen) can use the same setup. */
  public static void registerEngines() {
    Network bundled = NetFile.loadDefault();
    Engines.register("nnue", () -> new NnueSearch(bundled, "bundled nets/best.nnue"));
    Engines.registerOption(
        "EvalFile",
        "option name EvalFile type string default <bundled>",
        (searcher, value) -> {
          if (!(searcher instanceof NnueSearch nnue)) {
            return; // only the nnue engine has a network
          }
          if (value.isBlank() || value.equals("<bundled>")) {
            nnue.setNetwork(bundled, "bundled nets/best.nnue");
            return;
          }
          try {
            nnue.setNetwork(NetFile.load(Path.of(value)), value);
          } catch (IOException e) {
            throw new IllegalArgumentException("cannot read " + value + ": " + e.getMessage());
          }
        });
    if (bundled != null) {
      Engines.setDefault("nnue");
    }
  }
}
