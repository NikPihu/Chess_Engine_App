package com.gambyte.puzzle;

import com.gambyte.engine.UciEngine;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import phase1.Move;
import phase2.MoveGenerator;
import phase2.MoveList;
import phase2.Position;
import phase2.UciMoveParser;

/**
 * Finds tactics puzzles in positions from real games, using our own engine (no outside data).
 *
 * <p>A position becomes a puzzle when:
 *
 * <ol>
 *   <li>the engine finds a move that wins clearly (at least +2.5 pawns, or mate), and
 *   <li>it is the <em>only</em> such move: every other legal move, checked with a quick search,
 *       leaves at most +1 pawn. "Only move" positions are what make a puzzle fair: there is one
 *       answer to find.
 * </ol>
 *
 * The solution is the engine's main line, cut to at most three moves for the solver. The starting
 * rating is a rough guess from the line length and whether the first move is quiet; Glicko-2 then
 * corrects it as people solve.
 *
 * <p>Run it from the command line (it is not part of the web server):
 *
 * <pre>
 *   java -cp gambyte-server.jar -Dloader.main=com.gambyte.puzzle.PuzzleMiner \
 *        org.springframework.boot.loader.launch.PropertiesLauncher \
 *        &lt;engine path&gt; &lt;fen file&gt; &lt;out.csv&gt; [threads] [max positions]
 * </pre>
 *
 * Input: one FEN per line (anything after ';' is ignored). Output: Gambyte puzzle CSV
 * ({@code id,fen,moves,rating,themes}) for {@link PuzzleImporter}.
 */
public final class PuzzleMiner {

  private static final long DEEP_NODES = 400_000;
  private static final long QUICK_NODES = 25_000;
  private static final int WIN = 250;
  private static final int SECOND_BEST_MAX = 100;

  private PuzzleMiner() {}

  /** A found puzzle. */
  public record Found(String fen, List<String> moves, int rating, String themes) {}

  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.err.println("usage: PuzzleMiner <engine> <fens.txt> <out.csv> [threads] [maxPositions]");
      System.exit(2);
    }
    String engine = args[0];
    Path input = Path.of(args[1]);
    Path output = Path.of(args[2]);
    int threads = args.length > 3 ? Integer.parseInt(args[3]) : 4;
    int max = args.length > 4 ? Integer.parseInt(args[4]) : 20_000;

    // Spread the sample over the whole file: take every k-th line.
    long lines;
    try (var s = Files.lines(input)) {
      lines = s.count();
    }
    long step = Math.max(1, lines / max);
    List<String> fens = new ArrayList<>();
    try (BufferedReader r = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
      String line;
      long i = 0;
      while ((line = r.readLine()) != null && fens.size() < max) {
        if (i++ % step == 0) fens.add(line.split(";")[0].trim());
      }
    }
    System.err.printf("mining %d positions with %d engines%n", fens.size(), threads);

    AtomicInteger next = new AtomicInteger();
    AtomicInteger found = new AtomicInteger();
    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(output, StandardCharsets.UTF_8))) {
      out.println("id,fen,moves,rating,themes");
      ExecutorService pool = Executors.newFixedThreadPool(threads);
      for (int t = 0; t < threads; t++) {
        pool.submit(() -> {
          try (UciEngine uci = new UciEngine(engine, Map.of("Threads", "1", "Hash", "64"))) {
            int i;
            while ((i = next.getAndIncrement()) < fens.size()) {
              Found f = examine(uci, fens.get(i));
              if (f != null) {
                synchronized (out) {
                  int n = found.incrementAndGet();
                  out.printf("m%05d,%s,%s,%d,%s%n", n, f.fen(), String.join(" ", f.moves()), f.rating(), f.themes());
                  out.flush();
                }
              }
              if (i % 200 == 0) System.err.printf("%d / %d positions, %d puzzles%n", i, fens.size(), found.get());
            }
          } catch (Exception e) {
            System.err.println("worker failed: " + e);
          }
          return null;
        });
      }
      pool.shutdown();
      pool.awaitTermination(7, TimeUnit.DAYS);
    }
    System.err.printf("done: %d puzzles written to %s%n", found.get(), output);
  }

  /** Checks one position; returns a puzzle or null. */
  static Found examine(UciEngine uci, String fen) throws IOException {
    Position pos;
    try {
      pos = Position.fromFen(fen);
    } catch (RuntimeException e) {
      return null;
    }
    MoveList legal = new MoveList();
    MoveGenerator.generate(pos, legal);
    if (legal.size() < 2) return null;

    uci.newGame();
    UciEngine.Result deep = uci.search(fen, List.of(), "nodes " + DEEP_NODES, 60_000);
    int best = deep.scoreOrMate();
    if (best < WIN || deep.pv().isEmpty()) return null;
    String bestMove = deep.bestMove();

    // Is it the only winning move? Search every alternative briefly.
    int secondBest = Integer.MIN_VALUE;
    for (int i = 0; i < legal.size(); i++) {
      String m = Move.toUci(legal.get(i));
      if (m.equals(bestMove)) continue;
      UciEngine.Result r = uci.search(fen, List.of(m), "nodes " + QUICK_NODES, 30_000);
      secondBest = Math.max(secondBest, -r.scoreOrMate());
      if (secondBest > SECOND_BEST_MAX) return null;
    }

    // Solution: the main line, ending on our move, at most 5 plies (3 moves for the solver).
    List<String> pv = new ArrayList<>(deep.pv());
    int plies = Math.min(pv.size(), 5);
    if (plies % 2 == 0) plies--;
    List<String> solution = validate(fen, pv.subList(0, plies));
    if (solution == null) return null;

    boolean mate = deep.mateIn() != null && deep.mateIn() > 0;
    boolean quiet = isQuiet(pos, bestMove);
    int rating = 900 + 250 * (solution.size() / 2) + (quiet ? 300 : 0) + (mate ? 0 : 150);
    rating = Math.min(2600, rating + Math.max(0, 40 - legal.size()) * 3);
    return new Found(fen, solution, rating, themes(pos, solution, deep, quiet));
  }

  /** Makes sure the line is legal with our own rules (the engine's PV could be truncated). */
  private static List<String> validate(String fen, List<String> line) {
    Position p = Position.fromFen(fen);
    List<String> out = new ArrayList<>();
    for (String m : line) {
      int move = UciMoveParser.parse(p, m);
      if (move == Move.NULL) break;
      p.makeMove(move);
      out.add(m);
    }
    if (out.isEmpty()) return null;
    if (out.size() % 2 == 0) out.removeLast();
    return out;
  }

  private static boolean isQuiet(Position pos, String uci) {
    int move = UciMoveParser.parse(pos, uci);
    int target = Move.target(move);
    boolean capture = pos.board().pieceAt(target) != phase1.Piece.NONE;
    pos.makeMove(move);
    boolean check = pos.inCheck();
    pos.unmakeMove(move);
    return !capture && !check;
  }

  /** Simple themes: mate in N, advantage/crushing, game phase, length. */
  private static String themes(Position pos, List<String> solution, UciEngine.Result r, boolean quiet) {
    List<String> t = new ArrayList<>();
    if (r.mateIn() != null && r.mateIn() > 0) {
      t.add("mate");
      if (r.mateIn() <= 3) t.add("mateIn" + r.mateIn());
    } else {
      t.add(r.scoreOrMate() >= 600 ? "crushing" : "advantage");
    }
    int pieces = Long.bitCount(pos.board().occupied());
    t.add(pieces <= 12 ? "endgame" : pieces >= 28 ? "opening" : "middlegame");
    int moves = (solution.size() + 1) / 2;
    t.add(moves == 1 ? "oneMove" : moves == 2 ? "short" : "long");
    if (quiet) t.add("quietMove");
    return String.join(" ", t);
  }
}
