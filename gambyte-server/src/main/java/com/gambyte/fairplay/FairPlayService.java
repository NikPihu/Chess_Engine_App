package com.gambyte.fairplay;

import com.gambyte.engine.EngineLimits;
import com.gambyte.engine.EnginePool;
import com.gambyte.engine.UciEngine;
import com.gambyte.game.domain.GameRecord;
import com.gambyte.game.service.GameEvents.GameFinished;
import com.gambyte.game.service.GameService;
import com.gambyte.user.domain.User;
import com.gambyte.user.service.UserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import phase2.Position;
import phase2.UciMoveParser;

/**
 * Fair play: after every rated game between people, each player's moves are compared with our
 * engine's. Nobody is ever banned automatically - suspicious games are <em>flagged</em> for a
 * moderator, with the numbers that raised the flag.
 *
 * <p>Signals (only moves after the opening, ply 16+, are counted, since everyone plays book moves):
 *
 * <ul>
 *   <li><b>Top-engine match</b>: how often the player chose exactly the engine's move.
 *   <li><b>Average centipawn loss (ACPL)</b>: how much evaluation each move gave away on average.
 *       Strong humans lose ~20-40; engines ~5. Expected ACPL falls as rating rises, so the same
 *       numbers mean more for a 1200 than for a 2500.
 *   <li><b>Move-time variation</b>: humans think fast on easy moves and long on hard ones; a very
 *       even rhythm (low coefficient of variation) looks automated.
 *   <li><b>Tab focus losses</b>: switching away from the game many times (to another program).
 * </ul>
 *
 * The signals are combined into a 0-1 suspicion score; a game is flagged at 0.7 or above with at
 * least 15 moves checked. The weights are deliberately simple and documented, so moderators can
 * understand (and challenge) every flag.
 */
@Service
public class FairPlayService {

  private static final Logger log = LoggerFactory.getLogger(FairPlayService.class);
  private static final long NODES = 120_000;
  private static final int FIRST_PLY = 16;
  private static final double FLAG_AT = 0.7;

  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final GameService games;
  private final UserService users;
  private final EnginePool pool;

  public FairPlayService(JdbcTemplate jdbc, TransactionTemplate tx, GameService games, UserService users, EnginePool pool) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.games = games;
    this.users = users;
    this.pool = pool;
  }

  /** Queues checks for both players of a finished rated human game. */
  @EventListener
  public void onGameFinished(GameFinished e) {
    if (!e.rated()) return;
    Map<Long, User> both = users.getAll(List.of(e.whiteId(), e.blackId()));
    for (long id : List.of(e.whiteId(), e.blackId())) {
      User u = both.get(id);
      if (u == null || u.isBot()) continue;
      jdbc.update("INSERT INTO fairplay_checks (game_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING", e.gameId(), id);
    }
  }

  /** Works through the queue one check at a time, at low priority (every 5 s). */
  @Scheduled(fixedDelay = 5_000)
  public void work() {
    Map<String, Object> job = tx.execute(s -> {
      List<Map<String, Object>> rows = jdbc.queryForList("""
          SELECT game_id, user_id FROM fairplay_checks WHERE status = 'QUEUED'
          ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED""");
      if (rows.isEmpty()) return null;
      jdbc.update("UPDATE fairplay_checks SET status = 'RUNNING' WHERE game_id = ? AND user_id = ?",
          rows.getFirst().get("game_id"), rows.getFirst().get("user_id"));
      return rows.getFirst();
    });
    if (job == null) return;
    String gameId = (String) job.get("game_id");
    long userId = ((Number) job.get("user_id")).longValue();
    try {
      check(games.record(gameId), userId);
    } catch (Exception ex) {
      log.warn("Fair-play check {}/{} failed: {}", gameId, userId, ex.toString());
      jdbc.update("UPDATE fairplay_checks SET status = 'FAILED' WHERE game_id = ? AND user_id = ?", gameId, userId);
    }
  }

  /** Checks interrupted by a restart go back to the queue. */
  @EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
  public void requeueInterrupted() {
    jdbc.update("UPDATE fairplay_checks SET status = 'QUEUED' WHERE status = 'RUNNING'");
  }

  /** The statistics of one player in one game. */
  public record Stats(int moves, double acpl, double topMatch, double timeCv, int blurs, int rating, double suspicion) {}

  void check(GameRecord g, long userId) {
    int colour = g.getWhiteId() == userId ? 0 : 1;
    List<String> moves = g.moveList();
    List<Integer> times = g.moveTimeList();
    Position p = Position.fromFen(g.getStartFen());
    int checked = 0;
    int matches = 0;
    double lossSum = 0;
    List<Integer> myTimes = new ArrayList<>();
    for (int i = 0; i < moves.size(); i++) {
      String fen = p.toFen();
      boolean mine = (fen.contains(" w ") ? 0 : 1) == colour;
      if (mine && i >= FIRST_PLY) {
        UciEngine.Result before = pool.search(fen, List.of(), EngineLimits.nodes(NODES));
        // Already decided positions say nothing about cheating.
        if (Math.abs(before.scoreOrMate()) < 500) {
          UciEngine.Result after = pool.search(fen, List.of(moves.get(i)), EngineLimits.nodes(NODES));
          int loss = Math.max(0, before.scoreOrMate() - (-after.scoreOrMate()));
          lossSum += Math.min(loss, 1000);
          if (moves.get(i).equals(before.bestMove())) matches++;
          checked++;
          if (i < times.size()) myTimes.add(times.get(i));
        }
      }
      p.makeMove(UciMoveParser.parse(p, moves.get(i)));
    }
    int rating = colour == 0 ? nz(g.getWhiteRating()) : nz(g.getBlackRating());
    int blurs = colour == 0 ? g.getWhiteBlurs() : g.getBlackBlurs();
    Stats s = stats(checked, matches, lossSum, myTimes, blurs, rating);
    boolean flagged = s.moves() >= 15 && s.suspicion() >= FLAG_AT;
    jdbc.update("""
        UPDATE fairplay_checks SET status = 'DONE', moves_checked = ?, acpl = ?, top_match = ?, time_cv = ?,
          blurs = ?, rating = ?, suspicion = ?, flagged = ? WHERE game_id = ? AND user_id = ?""",
        s.moves(), s.acpl(), s.topMatch(), s.timeCv(), s.blurs(), s.rating(), s.suspicion(), flagged, g.getId(), userId);
    if (flagged) log.info("Fair-play flag: user {} in game {} (suspicion {})", userId, g.getId(), s.suspicion());
  }

  /** Combines the signals into a suspicion score in [0, 1]. Public for unit tests. */
  public static Stats stats(int moves, int matches, double lossSum, List<Integer> times, int blurs, int rating) {
    double acpl = moves == 0 ? 0 : lossSum / moves;
    double match = moves == 0 ? 0 : (double) matches / moves;
    double cv = coefficientOfVariation(times);
    // Rough expected ACPL for a human of this rating (≈ 90 at 1000, ≈ 35 at 2000, ≈ 15 at 2600).
    double expected = Math.max(12, 90 - (Math.max(1000, rating) - 1000) * 0.055);
    double accuracySignal = clamp((expected - acpl) / expected);           // far better than expected
    double matchSignal = clamp((match - 0.55) / 0.35);                      // 55% normal, 90% extreme
    double rhythmSignal = times.size() < 10 ? 0 : clamp((0.6 - cv) / 0.5);  // very even timing
    double blurSignal = clamp((blurs - 3) / 10.0);
    double suspicion = 0.4 * accuracySignal + 0.35 * matchSignal + 0.15 * rhythmSignal + 0.1 * blurSignal;
    return new Stats(moves, round(acpl), round(match), round(cv), blurs, rating, round(suspicion));
  }

  private static double coefficientOfVariation(List<Integer> times) {
    if (times.size() < 2) return 1;
    double mean = times.stream().mapToInt(Integer::intValue).average().orElse(0);
    if (mean <= 0) return 1;
    double var = times.stream().mapToDouble(t -> (t - mean) * (t - mean)).sum() / times.size();
    return Math.sqrt(var) / mean;
  }

  private static double clamp(double v) {
    return Math.max(0, Math.min(1, v));
  }

  private static double round(double v) {
    return Math.round(v * 1000) / 1000.0;
  }

  private static int nz(Integer v) {
    return v == null ? 1500 : v;
  }
}
