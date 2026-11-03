package datagen;

import gui.PgnReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import phase1.Colour;
import phase1.FenWriter;
import phase1.Move;
import phase2.Position;
import phase3.Game;
import phase3.San;
import phase3.SearchContext;
import phase3.SearchLimits;
import phase3.Searcher;
import phase6.MoveOrdering2;

/**
 * Turns real games (e.g. our Lichess bot's games against other engines and humans) into training
 * records, and finds our own blunders in them.
 *
 * <pre>
 *   java -cp chess_engine_v2/target/chess_engine_v2.jar datagen.PgnPositions
 *        --pgn tools/data/lichess --out tools/data/lichess.bin --player nikpihu
 *        [--nodes 50000] [--engine v2 | --engine nnue --net best.nnue] [--threads 14]
 *        [--blunders chess_engine_v2/tests/lichess-blunders.epd]
 * </pre>
 *
 * <ul>
 *   <li>Every quiet position is re-scored with a <b>deeper</b> search than in play (50k nodes, ~10x
 *       self-play), and labelled with the real game result: what actually happened against an
 *       opponent we didn't choose.
 *   <li><b>Blunder mining:</b> if one of {@code --player}'s moves made its own position worse by
 *       more than 150 cp according to the deep search, the position before that move gets 4x
 *       training weight and is written to the blunder suite (EPD with the better move).
 * </ul>
 */
public final class PgnPositions {

  static final int BLUNDER_CP = 150;

  public static void main(String[] args) throws Exception {
    Path pgnDir = Path.of("tools/data/lichess");
    Path out = Path.of("tools/data/lichess.bin");
    Path blunders = Path.of("chess_engine_v2/tests/lichess-blunders.epd");
    String player = "nikpihu";
    String engine = "v2";
    String net = null;
    int nodes = 50_000;
    int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    for (int i = 0; i + 1 < args.length; i += 2) {
      switch (args[i]) {
        case "--pgn" -> pgnDir = Path.of(args[i + 1]);
        case "--out" -> out = Path.of(args[i + 1]);
        case "--blunders" -> blunders = Path.of(args[i + 1]);
        case "--player" -> player = args[i + 1];
        case "--engine" -> engine = args[i + 1];
        case "--net" -> net = args[i + 1];
        case "--nodes" -> nodes = Integer.parseInt(args[i + 1]);
        case "--threads" -> threads = Integer.parseInt(args[i + 1]);
        default -> throw new IllegalArgumentException("unknown flag " + args[i]);
      }
    }

    List<PgnReader.PgnGame> games = new ArrayList<>();
    try (var files =
        Files.isDirectory(pgnDir) ? Files.list(pgnDir) : java.util.stream.Stream.of(pgnDir)) {
      for (Path f : files.filter(p -> p.toString().endsWith(".pgn")).toList()) {
        games.addAll(PgnReader.read(Files.readString(f, StandardCharsets.UTF_8)));
      }
    }
    System.out.printf(
        "%d games, re-scoring at %,d nodes on %d threads%n", games.size(), nodes, threads);

    ExecutorService pool = Executors.newFixedThreadPool(threads);
    final String engineName = engine;
    final String netPath = net;
    final int nodeBudget = nodes;
    final String who = player;
    ThreadLocal<Searcher> searchers =
        ThreadLocal.withInitial(
            () -> {
              try {
                return SelfPlay.createEngine(engineName, netPath);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
    List<Future<Result>> futures = new ArrayList<>();
    for (PgnReader.PgnGame g : games) {
      futures.add(pool.submit(() -> process(g, searchers.get(), nodeBudget, who)));
    }
    long records = 0;
    int blunderCount = 0;
    if (out.getParent() != null) {
      Files.createDirectories(out.getParent());
    }
    if (blunders.getParent() != null) {
      Files.createDirectories(blunders.getParent());
    }
    try (OutputStream file = Files.newOutputStream(out);
        PrintWriter epd =
            new PrintWriter(Files.newBufferedWriter(blunders, StandardCharsets.UTF_8))) {
      epd.println(
          "# Positions where our Lichess bot blundered (>"
              + BLUNDER_CP
              + " cp), with a better move.");
      epd.println(
          "# Written by datagen.PgnPositions; used as a regression suite for new networks.");
      for (Future<Result> f : futures) {
        Result r = f.get();
        file.write(r.records);
        records += r.records.length / Record.SIZE;
        for (String line : r.blunders) {
          epd.println(line);
          blunderCount++;
        }
      }
    }
    pool.shutdown();
    System.out.printf(
        "Wrote %,d positions to %s and %d blunders to %s%n", records, out, blunderCount, blunders);
  }

  record Result(byte[] records, List<String> blunders) {}

  static Result process(PgnReader.PgnGame g, Searcher searcher, int nodes, String player) {
    String resultTag = g.tags().getOrDefault("Result", "*");
    int result =
        switch (resultTag) {
          case "1-0" -> Adjudicator.WHITE_WINS;
          case "0-1" -> Adjudicator.BLACK_WINS;
          case "1/2-1/2" -> Adjudicator.DRAW;
          default -> -1;
        };
    if (result < 0 || g.moves().isEmpty()) {
      return new Result(new byte[0], List.of());
    }
    int ourColour =
        player.equalsIgnoreCase(g.tags().getOrDefault("White", ""))
            ? Colour.WHITE
            : player.equalsIgnoreCase(g.tags().getOrDefault("Black", "")) ? Colour.BLACK : -1;

    searcher.newGame();
    Game game = new Game(g.startFen());
    ScoreCapture capture = new ScoreCapture();
    int n = g.moves().size();
    int[] whiteScore = new int[n + 1];
    int[] bestMove = new int[n + 1];
    boolean[] usable = new boolean[n + 1];
    phase1.Board[] boards = new phase1.Board[n + 1];
    String[] fens = new String[n + 1];

    for (int ply = 0; ply <= n; ply++) {
      if (game.result().isOver()) {
        n = ply;
        break;
      }
      Position pos = game.copyOfPosition();
      SearchLimits limits = new SearchLimits();
      limits.nodes = nodes;
      capture.reset();
      int best = searcher.bestMove(pos, limits, new SearchContext(capture));
      int stm = game.position().sideToMove();
      whiteScore[ply] = stm == Colour.WHITE ? capture.score() : -capture.score();
      bestMove[ply] = best;
      boards[ply] = game.position().board().copy();
      fens[ply] = FenWriter.write(game.position().board());
      usable[ply] =
          ply >= 8
              && !game.position().inCheck()
              && !capture.isMate()
              && Math.abs(whiteScore[ply]) < 3000
              && best != Move.NULL
              && MoveOrdering2.isQuiet(game.position().board(), best);
      if (ply < g.moves().size()) {
        game.play(g.moves().get(ply));
      }
    }

    List<String> blunders = new ArrayList<>();
    ByteBuffer buffer = ByteBuffer.allocate((n + 1) * Record.SIZE);
    Game replay = new Game(g.startFen());
    for (int ply = 0; ply <= n; ply++) {
      int weight = 1;
      if (ply < n && ply < g.moves().size()) {
        int mover = replay.position().sideToMove();
        int sign = mover == Colour.WHITE ? 1 : -1;
        int loss = sign * (whiteScore[ply] - whiteScore[ply + 1]);
        int played = g.moves().get(ply);
        if (mover == ourColour && loss > BLUNDER_CP && played != bestMove[ply]) {
          weight = 4;
          String better = San.toSan(replay.position(), bestMove[ply]);
          String actual = San.toSan(replay.position(), played);
          blunders.add(
              fens[ply]
                  + " bm "
                  + better
                  + "; c0 \"played "
                  + actual
                  + ", lost "
                  + loss
                  + " cp\";");
        }
        replay.play(played);
      }
      if (usable[ply]) {
        Record.write(buffer, boards[ply], whiteScore[ply], result, weight);
      }
    }
    byte[] bytes = new byte[buffer.position()];
    buffer.flip();
    buffer.get(bytes);
    return new Result(bytes, blunders);
  }
}
