package com.gambyte.engine;

import com.gambyte.common.ApiException;
import com.gambyte.config.GambyteProperties;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import phase2.Position;
import phase3.SearchContext;
import phase3.SearchLimits;
import phase6.Search2;

/**
 * A small pool of warm engine processes shared by everything that needs the engine: computer
 * opponents, game review, the coach and fair-play checks.
 *
 * <p>Why a pool: starting an engine (and loading its network) takes ~100 ms and memory; keeping a
 * few running and lending them out makes each request fast and caps CPU/RAM use on a small server.
 * A {@link Semaphore} counts free engines; a caller waits up to {@code borrow-timeout-ms} for one,
 * then gets HTTP 503 "engine busy" instead of piling up work the box can't do (back-pressure).
 *
 * <p>Fallback: if the C++ binary is missing (for example a fresh clone without a build), the pool
 * uses the Java engine from chess_engine_v1 instead, so the site still works, just weaker.
 */
@Component
public class EnginePool {

  private static final Logger log = LoggerFactory.getLogger(EnginePool.class);

  private final GambyteProperties.Engine config;
  private final Semaphore permits;
  private final ConcurrentLinkedQueue<UciEngine> idle = new ConcurrentLinkedQueue<>();
  private final boolean nativeAvailable;

  public EnginePool(GambyteProperties props) {
    this.config = props.engine();
    this.permits = new Semaphore(Math.max(1, config.poolSize()), true);
    this.nativeAvailable = config.path() != null && new File(config.path()).canExecute();
    if (nativeAvailable) {
      log.info("Engine pool: {} x {} ({} threads, {} MB hash)",
          config.poolSize(), config.path(), config.threads(), config.hashMb());
    } else {
      log.warn("Engine binary not found at '{}': using the built-in Java engine (weaker). "
          + "Build chess_engine_v3 or set ENGINE_PATH.", config.path());
    }
  }

  /** True when the strong C++ engine is in use (false = Java fallback). */
  public boolean isNative() {
    return nativeAvailable;
  }

  /**
   * Searches one position.
   *
   * @throws ApiException 503 if every engine stays busy for the whole borrow timeout
   */
  public UciEngine.Result search(String fen, List<String> moves, EngineLimits limits) {
    boolean acquired;
    try {
      acquired = permits.tryAcquire(config.borrowTimeoutMs(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw busy();
    }
    if (!acquired) throw busy();
    try {
      return nativeAvailable ? searchNative(fen, moves, limits) : searchJava(fen, moves, limits);
    } finally {
      permits.release();
    }
  }

  private UciEngine.Result searchNative(String fen, List<String> moves, EngineLimits limits) {
    UciEngine engine = idle.poll();
    try {
      if (engine == null || !engine.isAlive()) engine = start();
      UciEngine.Result result = engine.search(fen, moves, limits.toUci(), limits.maxTimeMs());
      idle.add(engine); // healthy: back into the pool
      return result;
    } catch (IOException e) {
      // A broken or stuck process is thrown away; the next caller starts a fresh one.
      log.warn("Engine failed ({}), restarting it", e.getMessage());
      if (engine != null) engine.close();
      return searchJava(fen, moves, limits);
    }
  }

  private UciEngine start() throws IOException {
    Map<String, String> options = new LinkedHashMap<>();
    options.put("Threads", String.valueOf(config.threads()));
    options.put("Hash", String.valueOf(config.hashMb()));
    options.put("EvalFile", config.evalFile());
    options.put("SyzygyPath", config.syzygyPath());
    return new UciEngine(config.path(), options);
  }

  /** Java fallback: chess_engine_v1's best search (v2 features), same interface. */
  private static UciEngine.Result searchJava(String fen, List<String> moves, EngineLimits limits) {
    Position position = Position.fromFen(fen);
    for (String m : moves) position.makeMove(phase2.UciMoveParser.parse(position, m));
    SearchLimits sl;
    if (limits.nodes() > 0) {
      // The Java engine is ~20x slower than the C++ one; turn the node budget into a short time.
      sl = SearchLimits.moveTime(Math.min(2_000, Math.max(20, limits.nodes() / 500)));
    } else if (limits.moveTimeMs() > 0) {
      sl = SearchLimits.moveTime(limits.moveTimeMs());
    } else {
      sl = new SearchLimits();
      sl.whiteTimeMs = limits.whiteMs();
      sl.blackTimeMs = limits.blackMs();
      sl.whiteIncrementMs = limits.whiteIncMs();
      sl.blackIncrementMs = limits.blackIncMs();
    }
    String[] lastInfo = {null};
    int best = new Search2().bestMove(position, sl, new SearchContext(line -> {
      if (line.startsWith("info ") && line.contains(" pv ")) lastInfo[0] = line;
    }));
    String bestUci = best == phase1.Move.NULL ? "(none)" : phase1.Move.toUci(best);
    return UciEngine.parse(bestUci, lastInfo[0]);
  }

  private static ApiException busy() {
    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "engine_busy",
        "The engine is busy right now, try again in a moment");
  }

  @PreDestroy
  void shutdown() {
    UciEngine e;
    while ((e = idle.poll()) != null) e.close();
  }
}
