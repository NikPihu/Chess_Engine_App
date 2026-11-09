package com.gambyte.game.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Where running games live: a {@link ConcurrentHashMap} in memory (every move is served from here,
 * no database round trip), backed by a snapshot in Redis after every change so a server restart
 * or crash loses nothing. Postgres only sees finished games.
 *
 * <p>Snapshots are written on a background virtual thread, so a slow Redis (for example a free
 * hosted one far away) never delays a move.
 */
@Component
public class LiveGameStore {

  private static final Logger log = LoggerFactory.getLogger(LiveGameStore.class);
  private static final String PREFIX = "gambyte:live:";
  private static final Duration SNAPSHOT_TTL = Duration.ofDays(7);

  private final Map<String, LiveGame> games = new ConcurrentHashMap<>();
  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final ExecutorService writer = Executors.newVirtualThreadPerTaskExecutor();

  public LiveGameStore(StringRedisTemplate redis, ObjectMapper json) {
    this.redis = redis;
    this.json = json;
  }

  /** Everything needed to rebuild a {@link LiveGame}. */
  public record Snapshot(
      String id,
      LiveGame.Player white,
      LiveGame.Player black,
      String perf,
      boolean rated,
      String source,
      Long tournamentId,
      String startFen,
      long initialMs,
      long incrementMs,
      long whiteMs,
      long blackMs,
      int running,
      long runningSince,
      long createdAt,
      List<String> moves,
      List<String> sans,
      List<Integer> moveTimes) {}

  public void put(LiveGame game) {
    games.put(game.id(), game);
    save(game);
  }

  public Optional<LiveGame> get(String id) {
    return Optional.ofNullable(games.get(id));
  }

  public Collection<LiveGame> all() {
    return games.values();
  }

  public void remove(String id) {
    games.remove(id);
    writer.submit(() -> redis.delete(PREFIX + id));
  }

  /** Writes the snapshot in the background. */
  public void save(LiveGame game) {
    Snapshot s;
    synchronized (game) {
      long now = System.currentTimeMillis();
      ChessClock c = game.clock();
      s =
          new Snapshot(
              game.id(),
              game.white(),
              game.black(),
              game.perf(),
              game.rated(),
              game.source(),
              game.tournamentId(),
              game.startFen(),
              c.initialMs(),
              c.incrementMs(),
              c.remaining(0, now),
              c.remaining(1, now),
              c.running(),
              now,
              game.createdAt(),
              game.moves(),
              game.sans(),
              game.moveTimes());
    }
    writer.submit(
        () -> {
          try {
            redis.opsForValue().set(PREFIX + s.id(), json.writeValueAsString(s), SNAPSHOT_TTL);
          } catch (Exception e) {
            log.warn("Could not snapshot game {}: {}", s.id(), e.getMessage());
          }
        });
  }

  /** Reads a snapshot back (used at start-up for games that were running). */
  public Optional<LiveGame> restore(String id) {
    try {
      String raw = redis.opsForValue().get(PREFIX + id);
      if (raw == null) return Optional.empty();
      Snapshot s = json.readValue(raw, Snapshot.class);
      LiveGame game =
          new LiveGame(
              s.id(), s.white(), s.black(), s.perf(), s.rated(), s.source(), s.tournamentId(),
              s.startFen(), s.initialMs(), s.incrementMs(), s.createdAt());
      game.replay(s.moves(), s.moveTimes(), s.sans());
      // Restore the clocks. The downtime is not charged to the player whose clock was running.
      ChessClock restored = game.clock();
      restored.addTime(0, s.whiteMs() - restored.remaining(0, s.runningSince()));
      restored.addTime(1, s.blackMs() - restored.remaining(1, s.runningSince()));
      if (s.running() >= 0) restored.start(s.running(), System.currentTimeMillis());
      games.put(id, game);
      return Optional.of(game);
    } catch (Exception e) {
      log.warn("Could not restore game {}: {}", id, e.getMessage());
      return Optional.empty();
    }
  }
}
