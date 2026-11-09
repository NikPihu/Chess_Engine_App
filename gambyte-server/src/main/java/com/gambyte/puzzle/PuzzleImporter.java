package com.gambyte.puzzle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import phase2.Position;
import phase2.UciMoveParser;

/**
 * Loads puzzles from CSV files. Two formats are understood:
 *
 * <ul>
 *   <li><b>Gambyte</b> ({@link PuzzleMiner} output): {@code id,fen,moves,rating,themes}
 *   <li><b>Lichess</b> (the CC0 database, https://database.lichess.org/#puzzles): {@code
 *       PuzzleId,FEN,Moves,Rating,...,Themes,...}. There the FEN is <em>before</em> the opponent's
 *       last move, which is the first entry of Moves; it becomes our "setup move".
 * </ul>
 *
 * On start-up, if the table is empty, the puzzles shipped in {@code resources/puzzles/seed.csv} are
 * loaded, so a fresh install has puzzles immediately. Bigger sets are imported by an admin with
 * {@code POST /api/admin/puzzles/import?path=...}.
 */
@Component
public class PuzzleImporter {

  private static final Logger log = LoggerFactory.getLogger(PuzzleImporter.class);
  private static final int BATCH = 1000;

  private final PuzzleRepository repo;
  private final TransactionTemplate tx;

  public PuzzleImporter(PuzzleRepository repo, TransactionTemplate tx) {
    this.repo = repo;
    this.tx = tx;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void seedIfEmpty() {
    if (repo.count() > 0) return;
    ClassPathResource seed = new ClassPathResource("puzzles/seed.csv");
    if (!seed.exists()) return;
    try (InputStream in = seed.getInputStream()) {
      int n = importCsv(in, Integer.MAX_VALUE);
      log.info("Loaded {} seed puzzles", n);
    } catch (IOException e) {
      log.warn("Could not load seed puzzles: {}", e.getMessage());
    }
  }

  /** Imports up to {@code max} puzzles from a file on the server's disk. */
  public int importFile(Path path, int max) throws IOException {
    try (InputStream in = Files.newInputStream(path)) {
      return importCsv(in, max);
    }
  }

  int importCsv(InputStream in, int max) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    String header = reader.readLine();
    if (header == null) return 0;
    boolean lichess = header.startsWith("PuzzleId");
    List<Puzzle> batch = new ArrayList<>(BATCH);
    int count = 0;
    String line;
    while ((line = reader.readLine()) != null && count < max) {
      Puzzle p = lichess ? fromLichess(line) : fromGambyte(line);
      if (p == null) continue;
      batch.add(p);
      count++;
      if (batch.size() == BATCH) save(batch);
    }
    save(batch);
    return count;
  }

  private void save(List<Puzzle> batch) {
    if (batch.isEmpty()) return;
    List<Puzzle> copy = List.copyOf(batch);
    batch.clear();
    tx.executeWithoutResult(s -> repo.saveAll(copy.stream().filter(p -> !repo.existsById(p.getId())).toList()));
  }

  private static Puzzle fromGambyte(String line) {
    String[] f = line.split(",", -1);
    if (f.length < 5) return null;
    return new Puzzle(f[0], f[1], null, f[2], Double.parseDouble(f[3]), f[4], "mined");
  }

  /** Lichess columns: PuzzleId,FEN,Moves,Rating,RatingDeviation,Popularity,NbPlays,Themes,... */
  private static Puzzle fromLichess(String line) {
    String[] f = line.split(",", -1);
    if (f.length < 8) return null;
    String[] moves = f[2].split(" ");
    if (moves.length < 2) return null;
    try {
      Position p = Position.fromFen(f[1]);
      p.makeMove(UciMoveParser.parse(p, moves[0]));
      String solution = String.join(" ", java.util.Arrays.copyOfRange(moves, 1, moves.length));
      return new Puzzle("l" + f[0], p.toFen(), moves[0], solution, Double.parseDouble(f[3]), f[7], "lichess");
    } catch (RuntimeException e) {
      return null;
    }
  }
}
