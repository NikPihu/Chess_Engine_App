package datagen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import phase1.Move;
import phase2.Position;
import phase3.San;
import phase3.SearchContext;
import phase3.SearchLimits;
import phase3.Searcher;

/**
 * Safety gate for new networks: how many of the Lichess bot's past blunders does an engine now get
 * right?
 *
 * <pre>
 *   java -cp chess_engine_v2.jar datagen.BlunderSuite --net chess_engine_v2/nets/gen-3.nnue
 *        [--epd chess_engine_v2/tests/lichess-blunders.epd] [--nodes 20000] [--threads 12]
 * </pre>
 *
 * Each EPD line is a position where the bot played a bad move, with the better move ({@code bm})
 * found by a deep search. A position counts as solved when the engine, at fixed nodes, plays that
 * move. Prints {@code SOLVED <n> <total>} as the last line (read by train/loop.py).
 */
public final class BlunderSuite {

  public static void main(String[] args) throws Exception {
    Path epd = Path.of("chess_engine_v2/tests/lichess-blunders.epd");
    String engine = "nnue";
    String net = null;
    int nodes = 20_000;
    int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 4);
    for (int i = 0; i + 1 < args.length; i += 2) {
      switch (args[i]) {
        case "--epd" -> epd = Path.of(args[i + 1]);
        case "--engine" -> engine = args[i + 1];
        case "--net" -> net = args[i + 1];
        case "--nodes" -> nodes = Integer.parseInt(args[i + 1]);
        case "--threads" -> threads = Integer.parseInt(args[i + 1]);
        default -> throw new IllegalArgumentException("unknown flag " + args[i]);
      }
    }
    List<String[]> positions = read(epd);
    int[] result = solve(positions, engine, net, nodes, threads);
    System.out.printf("SOLVED %d %d%n", result[0], result[1]);
  }

  /** Parsed lines: {fen, best move SAN}. Comment lines (#) are skipped. */
  public static List<String[]> read(Path epd) throws java.io.IOException {
    List<String[]> out = new ArrayList<>();
    if (!Files.exists(epd)) {
      return out;
    }
    for (String line : Files.readAllLines(epd, StandardCharsets.UTF_8)) {
      int bm = line.indexOf(" bm ");
      if (line.startsWith("#") || bm < 0) {
        continue;
      }
      String fen = line.substring(0, bm).trim();
      String move = line.substring(bm + 4).split(";")[0].trim();
      out.add(new String[] {fen, move});
    }
    return out;
  }

  /**
   * @return {solved, total}
   */
  public static int[] solve(
      List<String[]> positions, String engine, String net, int nodes, int threads)
      throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    ThreadLocal<Searcher> searchers =
        ThreadLocal.withInitial(
            () -> {
              try {
                return SelfPlay.createEngine(engine, net);
              } catch (java.io.IOException e) {
                throw new RuntimeException(e);
              }
            });
    List<Future<Boolean>> futures = new ArrayList<>();
    for (String[] p : positions) {
      futures.add(
          pool.submit(
              () -> {
                Position pos = Position.fromFen(p[0]);
                int expected = San.parse(pos, p[1]);
                if (expected == Move.NULL) {
                  return false;
                }
                Searcher s = searchers.get();
                s.newGame();
                SearchLimits limits = new SearchLimits();
                limits.nodes = nodes;
                return s.bestMove(pos, limits, SearchContext.silent()) == expected;
              }));
    }
    int solved = 0;
    for (Future<Boolean> f : futures) {
      if (f.get()) {
        solved++;
      }
    }
    pool.shutdown();
    return new int[] {solved, positions.size()};
  }
}
