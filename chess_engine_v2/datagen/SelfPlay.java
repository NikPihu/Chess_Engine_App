package datagen;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import nnue.NetFile;
import phase1.Colour;
import phase1.Move;
import phase2.MoveGenerator;
import phase2.MoveList;
import phase2.Position;
import phase3.Game;
import phase3.GameResult;
import phase3.SearchContext;
import phase3.SearchLimits;
import phase3.Searcher;
import phase6.MoveOrdering2;
import phase6.Search2;
import search.NnueSearch;

/**
 * Self-play data generation: the engine plays thousands of games against itself and every suitable
 * position becomes a training example (position, the engine's search score, final game result).
 *
 * <pre>
 *   java -cp chess_engine_v2/target/chess_engine_v2.jar datagen.SelfPlay
 *        --positions 3000000 --out tools/data/gen-0.bin [--engine v2 | --engine nnue --net gen-1.nnue]
 *        [--nodes 5000] [--threads 14] [--seed 1]
 * </pre>
 *
 * <ul>
 *   <li><b>Openings:</b> 8 random legal plies, then the engine takes over. Games whose start is
 *       already lopsided (|score| > 400) are thrown away. Random openings give the variety a
 *       network needs; otherwise every game would follow the engine's favourite lines.
 *   <li><b>Fixed nodes per move</b> (not fixed time), so results don't depend on how busy the
 *       machine is and every thread does equal work.
 *   <li><b>Which positions are kept:</b> not in check, best move is quiet (no capture or
 *       promotion), at least 10 plies in, |score| &lt; 3000. Tactical positions are left to the
 *       search; the network should learn quiet positional judgement.
 * </ul>
 */
public final class SelfPlay {

  private static final int RANDOM_PLIES = 8;
  private static final int MIN_PLY = 10;
  private static final int MAX_PLIES = 400;

  public static void main(String[] args) throws Exception {
    long target = 1_000_000;
    Path out = Path.of("tools/data/selfplay.bin");
    String engine = "v2";
    String net = null;
    int nodes = 5000;
    int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    long seed = System.nanoTime();
    for (int i = 0; i + 1 < args.length; i += 2) {
      switch (args[i]) {
        case "--positions" -> target = Long.parseLong(args[i + 1]);
        case "--out" -> out = Path.of(args[i + 1]);
        case "--engine" -> engine = args[i + 1];
        case "--net" -> net = args[i + 1];
        case "--nodes" -> nodes = Integer.parseInt(args[i + 1]);
        case "--threads" -> threads = Integer.parseInt(args[i + 1]);
        case "--seed" -> seed = Long.parseLong(args[i + 1]);
        default -> throw new IllegalArgumentException("unknown flag " + args[i]);
      }
    }
    if (out.getParent() != null) {
      Files.createDirectories(out.getParent());
    }
    System.out.printf(
        "Self-play: %,d positions -> %s  engine=%s%s  nodes=%d  threads=%d%n",
        target, out, engine, net == null ? "" : " net=" + net, nodes, threads);

    AtomicLong written = new AtomicLong();
    AtomicLong games = new AtomicLong();
    long start = System.currentTimeMillis();
    try (OutputStream file =
        new BufferedOutputStream(
            Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.APPEND),
            1 << 20)) {
      List<Thread> workers = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        Searcher searcher = createEngine(engine, net);
        Worker w =
            new Worker(searcher, nodes, new Random(seed + 7919L * t), file, written, games, target);
        Thread thread = new Thread(w, "selfplay-" + t);
        workers.add(thread);
        thread.start();
      }
      while (workers.stream().anyMatch(Thread::isAlive)) {
        Thread.sleep(10_000);
        long n = Math.min(written.get(), target);
        double secs = (System.currentTimeMillis() - start) / 1000.0;
        double rate = n / Math.max(1, secs);
        System.out.printf(
            "%,d / %,d positions  (%,d games, %,.0f pos/s, ~%.0f min left)%n",
            n, target, games.get(), rate, (target - n) / Math.max(1, rate) / 60);
      }
    }
    System.out.printf("Done: %,d positions from %,d games.%n", written.get(), games.get());
  }

  static Searcher createEngine(String engine, String net) throws IOException {
    return switch (engine) {
      case "v2" -> new Search2();
      case "nnue" ->
          new NnueSearch(
              net == null ? NetFile.loadDefault() : NetFile.load(Path.of(net)),
              net == null ? "bundled" : net);
      default -> throw new IllegalArgumentException("engine must be v2 or nnue");
    };
  }

  /** Plays games one after another until the shared target is reached. */
  static final class Worker implements Runnable {
    private final Searcher searcher;
    private final int nodes;
    private final Random random;
    private final OutputStream file;
    private final AtomicLong written;
    private final AtomicLong games;
    private final long target;
    private final ScoreCapture capture = new ScoreCapture();

    Worker(
        Searcher searcher,
        int nodes,
        Random random,
        OutputStream file,
        AtomicLong written,
        AtomicLong games,
        long target) {
      this.searcher = searcher;
      this.nodes = nodes;
      this.random = random;
      this.file = file;
      this.written = written;
      this.games = games;
      this.target = target;
    }

    @Override
    public void run() {
      try {
        while (written.get() < target) {
          byte[] data = playGame();
          if (data.length > 0) {
            synchronized (file) {
              if (written.get() < target) {
                file.write(data);
                written.addAndGet(data.length / Record.SIZE);
              }
            }
          }
          games.incrementAndGet();
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    /** One game; returns its records (all labelled with the final result). */
    byte[] playGame() {
      searcher.newGame();
      Game game = new Game();
      MoveList moves = new MoveList();
      for (int i = 0; i < RANDOM_PLIES; i++) {
        MoveGenerator.generate(game.position(), moves);
        if (moves.size() == 0) {
          return new byte[0];
        }
        game.play(moves.get(random.nextInt(moves.size())));
      }

      List<Object[]> kept = new ArrayList<>(); // {fenBoardCopy, whiteScore}
      Adjudicator adjudicator = new Adjudicator();
      int result = Adjudicator.NONE;
      boolean first = true;
      while (result == Adjudicator.NONE) {
        GameResult rules = game.result();
        if (rules.isOver()) {
          result =
              rules == GameResult.WHITE_WINS
                  ? Adjudicator.WHITE_WINS
                  : rules == GameResult.BLACK_WINS ? Adjudicator.BLACK_WINS : Adjudicator.DRAW;
          break;
        }
        if (game.moves().size() >= MAX_PLIES) {
          result = Adjudicator.DRAW;
          break;
        }
        Position pos = game.copyOfPosition();
        SearchLimits limits = new SearchLimits();
        limits.nodes = nodes;
        capture.reset();
        int move = searcher.bestMove(pos, limits, new SearchContext(capture));
        if (move == Move.NULL) {
          result = Adjudicator.DRAW;
          break;
        }
        int stm = game.position().sideToMove();
        int whiteScore = stm == Colour.WHITE ? capture.score() : -capture.score();
        if (first && Math.abs(whiteScore) > 400) {
          return new byte[0]; // lopsided random opening: not useful
        }
        first = false;

        int ply = game.moves().size();
        boolean quiet = MoveOrdering2.isQuiet(game.position().board(), move);
        if (ply >= MIN_PLY
            && quiet
            && !capture.isMate()
            && Math.abs(whiteScore) < 3000
            && !game.position().inCheck()) {
          kept.add(new Object[] {game.position().board().copy(), whiteScore});
        }
        result = adjudicator.update(whiteScore, capture.isMate(), ply);
        game.play(move);
      }

      ByteBuffer buffer = ByteBuffer.allocate(kept.size() * Record.SIZE);
      for (Object[] k : kept) {
        Record.write(buffer, (phase1.Board) k[0], (Integer) k[1], result, 1);
      }
      return buffer.array();
    }
  }
}
