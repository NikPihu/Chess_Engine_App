package com.gambyte.engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One running engine process (chess_engine_v3) that we talk to in UCI, the text protocol every
 * chess engine understands: we write commands to its stdin and read its answers from stdout.
 *
 * <pre>
 *   position fen &lt;fen&gt; moves e2e4 e7e5     set up the board
 *   go nodes 20000                         think
 *   info depth 12 score cp 31 ... pv g1f3  (progress lines; we keep the last one)
 *   bestmove g1f3                          the answer
 * </pre>
 *
 * Not thread-safe: {@link EnginePool} hands each process to one caller at a time.
 */
public final class UciEngine implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(UciEngine.class);

  private final Process process;
  private final BufferedReader out;
  private final BufferedWriter in;

  /** What the engine answered for one position. */
  public record Result(
      String bestMove, Integer scoreCp, Integer mateIn, int depth, long nodes, List<String> pv) {

    /**
     * The score in centipawns from the point of view of the side to move; a mate counts as ±10000
     * minus the distance, so "mate in 2" ranks above "mate in 5".
     */
    public int scoreOrMate() {
      if (mateIn != null) return mateIn > 0 ? 10_000 - mateIn : -10_000 - mateIn;
      return scoreCp == null ? 0 : scoreCp;
    }
  }

  /** Starts the process and sets its options (Threads, Hash, EvalFile, SyzygyPath...). */
  public UciEngine(String path, Map<String, String> options) throws IOException {
    process = new ProcessBuilder(path).redirectErrorStream(true).start();
    out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    in = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    send("uci");
    readUntil("uciok", 10_000);
    for (var option : options.entrySet()) {
      if (option.getValue() != null && !option.getValue().isBlank()) {
        send("setoption name " + option.getKey() + " value " + option.getValue());
      }
    }
    ready();
  }

  /** Clears the engine's memory of earlier games (call before each new game or analysis). */
  public void newGame() throws IOException {
    send("ucinewgame");
    ready();
  }

  /**
   * Searches a position.
   *
   * @param fen the starting position of the game
   * @param moves moves played since then, in UCI notation
   * @param go the limits after "go", e.g. "nodes 20000" or "wtime 60000 btime 58000 winc 0 binc 0"
   * @param timeoutMs give up (and restart the engine) if no answer comes in this time
   */
  public Result search(String fen, List<String> moves, String go, long timeoutMs) throws IOException {
    StringBuilder position = new StringBuilder("position fen ").append(fen);
    if (!moves.isEmpty()) position.append(" moves ").append(String.join(" ", moves));
    send(position.toString());
    send("go " + go);

    String lastInfo = null;
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (true) {
      String line = readLine(deadline);
      if (line.startsWith("info ") && line.contains(" pv ")) {
        lastInfo = line;
      } else if (line.startsWith("bestmove")) {
        String[] parts = line.split("\\s+");
        String best = parts.length > 1 ? parts[1] : "(none)";
        return parse(best, lastInfo);
      }
    }
  }

  public boolean isAlive() {
    return process.isAlive();
  }

  @Override
  public void close() {
    try {
      send("quit");
      if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly();
    } catch (IOException | InterruptedException e) {
      process.destroyForcibly();
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
    }
  }

  // ------------------------------------------------------------------ protocol helpers

  private void ready() throws IOException {
    send("isready");
    readUntil("readyok", 30_000); // loading a network or tablebases can take a moment
  }

  private void send(String command) throws IOException {
    in.write(command);
    in.write('\n');
    in.flush();
  }

  private void readUntil(String expected, long timeoutMs) throws IOException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (!readLine(deadline).startsWith(expected)) {
      // skip id/option/info lines
    }
  }

  /**
   * Reads one line, failing if the deadline passes. Polling {@code ready()} keeps this simple and
   * avoids a reader thread per engine; engines answer in milliseconds, so the 1 ms sleep is fine.
   */
  private String readLine(long deadline) throws IOException {
    while (!out.ready()) {
      if (!process.isAlive()) throw new IOException("engine process exited");
      if (System.currentTimeMillis() > deadline) throw new IOException("engine timed out");
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("interrupted");
      }
    }
    String line = out.readLine();
    if (line == null) throw new IOException("engine closed its output");
    return line;
  }

  /** Pulls score, depth, nodes and principal variation out of the last "info" line. */
  static Result parse(String bestMove, String info) {
    Integer cp = null;
    Integer mate = null;
    int depth = 0;
    long nodes = 0;
    List<String> pv = List.of();
    if (info != null) {
      String[] t = info.split("\\s+");
      for (int i = 0; i < t.length - 1; i++) {
        switch (t[i]) {
          case "depth" -> depth = Integer.parseInt(t[i + 1]);
          case "nodes" -> nodes = Long.parseLong(t[i + 1]);
          case "cp" -> cp = Integer.parseInt(t[i + 1]);
          case "mate" -> mate = Integer.parseInt(t[i + 1]);
          case "pv" -> {
            pv = List.of(java.util.Arrays.copyOfRange(t, i + 1, t.length));
            i = t.length;
          }
          default -> {}
        }
      }
    }
    if (pv.isEmpty() && !"(none)".equals(bestMove)) pv = List.of(bestMove);
    log.trace("engine: best {} cp {} mate {} depth {}", bestMove, cp, mate, depth);
    return new Result(bestMove, cp, mate, depth, nodes, pv);
  }
}
