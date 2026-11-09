package com.gambyte.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gambyte.common.ApiException;
import com.gambyte.config.GambyteProperties;
import com.gambyte.engine.EngineLimits;
import com.gambyte.engine.EnginePool;
import com.gambyte.engine.UciEngine;
import com.gambyte.game.domain.GameRecord;
import com.gambyte.game.domain.GameStatus;
import com.gambyte.game.service.GameService;
import com.gambyte.user.service.UserService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import phase2.Position;
import phase2.UciMoveParser;

/**
 * Game Review: the engine goes through a finished game move by move, rates every move, computes
 * each side's accuracy, picks the key moments and has the coach explain the mistakes.
 *
 * <p><b>How a move is rated.</b> Centipawns are turned into <em>winning chances</em> (the same
 * logistic curve Lichess uses), because losing 2 pawns matters a lot at 0.0 and not at all at
 * +15. The drop in winning chances for the mover decides the label: under 2% "excellent", under
 * 10% "good", then inaccuracy (10-20), mistake (20-30) and blunder (30+). Accuracy per move is a
 * smooth function of the same drop, averaged over the game.
 *
 * <p><b>Background work.</b> Requests are queued in the {@code game_reviews} table; {@link
 * #work()} claims one at a time and runs it on the engine pool, so a burst of requests can never
 * overload the server and none are lost in a restart.
 */
@Service
public class ReviewService {

  private static final Logger log = LoggerFactory.getLogger(ReviewService.class);
  private static final long NODES = 150_000;

  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final GameService games;
  private final EnginePool pool;
  private final UserService users;
  private final ObjectMapper json;
  private final GambyteProperties props;

  public ReviewService(
      JdbcTemplate jdbc,
      TransactionTemplate tx,
      GameService games,
      EnginePool pool,
      UserService users,
      ObjectMapper json,
      GambyteProperties props) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.games = games;
    this.pool = pool;
    this.users = users;
    this.json = json;
    this.props = props;
  }

  /** One rated move. Evaluations are from White's side in centipawns. */
  public record MoveReview(
      int ply,
      String san,
      String color,
      String classification,
      String symbol,
      int evalBefore,
      int evalAfter,
      String bestSan,
      List<String> bestLine,
      double winChanceLost,
      String comment) {}

  public record SideSummary(double accuracy, Map<String, Integer> counts) {}

  /** A finished review. {@code evals[i]} is the evaluation after i plies (0 = start). */
  public record Review(
      String gameId,
      SideSummary white,
      SideSummary black,
      List<Integer> evals,
      List<MoveReview> moves,
      List<Integer> keyMoments) {}

  /** Status, plus the review once done. */
  public record ReviewStatus(String status, Review review, Integer queuePosition) {}

  // ------------------------------------------------------------------ requests

  /** Queues a review (idempotent: an existing one is returned). */
  public ReviewStatus request(String gameId, long userId) {
    GameRecord g = games.record(gameId);
    if (g.getStatus() == GameStatus.STARTED) throw ApiException.badRequest("game_running", "Finish the game first");
    if (g.getPly() < 2) throw ApiException.badRequest("too_short", "This game is too short to review");
    ReviewStatus existing = status(gameId);
    if (existing != null && !"FAILED".equals(existing.status())) return existing;

    if (!users.get(userId).isPremium()) {
      Integer today = jdbc.queryForObject(
          "SELECT count(*) FROM game_reviews WHERE requested_by = ? AND created_at > ?", Integer.class,
          userId, Timestamp.from(Instant.now().truncatedTo(ChronoUnit.DAYS)));
      if (today != null && today >= props.limits().freeReviewsPerDay()) {
        throw ApiException.premiumRequired("Free accounts get one Game Review a day - go Premium for unlimited reviews");
      }
    }
    jdbc.update("""
        INSERT INTO game_reviews (game_id, requested_by, status) VALUES (?, ?, 'QUEUED')
        ON CONFLICT (game_id) DO UPDATE SET status = 'QUEUED', requested_by = EXCLUDED.requested_by,
            created_at = now(), result = NULL""", gameId, userId);
    return status(gameId);
  }

  public ReviewStatus status(String gameId) {
    List<ReviewStatus> rows = jdbc.query(
        "SELECT status, result::text AS result, created_at FROM game_reviews WHERE game_id = ?",
        (rs, i) -> {
          String status = rs.getString("status");
          Review review = null;
          String raw = rs.getString("result");
          if (raw != null) {
            try {
              review = json.readValue(raw, Review.class);
            } catch (Exception e) {
              status = "FAILED";
            }
          }
          Integer position = null;
          if ("QUEUED".equals(status)) {
            position = jdbc.queryForObject(
                "SELECT count(*) FROM game_reviews WHERE status = 'QUEUED' AND created_at <= ?",
                Integer.class, rs.getTimestamp("created_at"));
          }
          return new ReviewStatus(status, review, position);
        },
        gameId);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  // ------------------------------------------------------------------ worker

  /** Claims and runs the oldest queued review, if any (every 2 seconds). */
  @Scheduled(fixedDelay = 2_000)
  public void work() {
    String gameId = tx.execute(s -> {
      List<String> ids = jdbc.queryForList("""
          SELECT game_id FROM game_reviews WHERE status = 'QUEUED'
          ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED""", String.class);
      if (ids.isEmpty()) return null;
      jdbc.update("UPDATE game_reviews SET status = 'RUNNING' WHERE game_id = ?", ids.getFirst());
      return ids.getFirst();
    });
    if (gameId == null) return;
    try {
      Review review = analyse(games.record(gameId));
      jdbc.update("UPDATE game_reviews SET status = 'DONE', result = ?::jsonb, finished_at = now() WHERE game_id = ?",
          json.writeValueAsString(review), gameId);
    } catch (Exception e) {
      log.warn("Review of {} failed: {}", gameId, e.toString());
      jdbc.update("UPDATE game_reviews SET status = 'FAILED', finished_at = now() WHERE game_id = ?", gameId);
    }
  }

  /** On start-up, reviews left RUNNING by a crash go back to the queue. */
  @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
  public void requeueInterrupted() {
    jdbc.update("UPDATE game_reviews SET status = 'QUEUED' WHERE status = 'RUNNING'");
  }

  // ------------------------------------------------------------------ the analysis

  Review analyse(GameRecord game) {
    List<String> moves = game.moveList();
    List<String> fens = new ArrayList<>();
    Position p = Position.fromFen(game.getStartFen());
    fens.add(p.toFen());
    for (String m : moves) {
      p.makeMove(UciMoveParser.parse(p, m));
      fens.add(p.toFen());
    }

    // Engine verdict for every position (side to move's view), then converted to White's view.
    List<UciEngine.Result> results = new ArrayList<>();
    List<Integer> whiteEvals = new ArrayList<>();
    for (int i = 0; i < fens.size(); i++) {
      boolean terminal = i == fens.size() - 1 && game.getStatus() == GameStatus.MATE;
      UciEngine.Result r = terminal
          ? new UciEngine.Result("(none)", null, 0, 0, 0, List.of())
          : pool.search(fens.get(i), List.of(), EngineLimits.nodes(NODES));
      results.add(r);
      int stm = r.scoreOrMate();
      boolean whiteToMove = fens.get(i).contains(" w ");
      int white = terminal ? (whiteToMove ? -10_000 : 10_000) : (whiteToMove ? stm : -stm);
      whiteEvals.add(Math.max(-10_000, Math.min(10_000, white)));
    }

    List<String> sans = AnalysisService.toSan(game.getStartFen(), moves);
    List<MoveReview> reviews = new ArrayList<>();
    double[] accuracySum = new double[2];
    int[] accuracyN = new int[2];
    List<Map<String, Integer>> counts = List.of(new LinkedHashMap<>(), new LinkedHashMap<>());

    for (int i = 0; i < moves.size(); i++) {
      int colour = fens.get(i).contains(" w ") ? 0 : 1;
      int sign = colour == 0 ? 1 : -1;
      double before = winChance(sign * whiteEvals.get(i));
      double after = winChance(sign * whiteEvals.get(i + 1));
      double drop = Math.max(0, before - after);
      UciEngine.Result best = results.get(i);
      boolean isBest = moves.get(i).equals(best.bestMove());
      String cls = classify(drop, isBest);
      double accuracy = Math.max(0, Math.min(100, 103.1668 * Math.exp(-0.04354 * drop) - 3.1669));
      accuracySum[colour] += accuracy;
      accuracyN[colour]++;
      counts.get(colour).merge(cls, 1, Integer::sum);

      String comment = null;
      if (drop >= 10) {
        UciEngine.Result reply = results.get(i + 1);
        comment = CoachExplainer.explain(fens.get(i), moves.get(i), best.pv(), reply.pv(),
            reply.mateIn(), best.mateIn(), drop);
      }
      List<String> bestLine = AnalysisService.toSan(fens.get(i), best.pv());
      reviews.add(new MoveReview(i + 1, sans.get(i), colour == 0 ? "white" : "black", cls, symbol(cls),
          whiteEvals.get(i), whiteEvals.get(i + 1), bestLine.isEmpty() ? null : bestLine.getFirst(),
          bestLine.subList(0, Math.min(6, bestLine.size())), Math.round(drop * 10) / 10.0, comment));
    }

    // Key moments: the three biggest swings in winning chances.
    List<Integer> key = IntStream.range(0, reviews.size()).boxed()
        .sorted(Comparator.comparingDouble((Integer i) -> -reviews.get(i).winChanceLost()))
        .filter(i -> reviews.get(i).winChanceLost() >= 10)
        .limit(3).map(i -> i + 1).sorted().toList();

    return new Review(game.getId(),
        new SideSummary(round(accuracySum[0], accuracyN[0]), counts.get(0)),
        new SideSummary(round(accuracySum[1], accuracyN[1]), counts.get(1)),
        whiteEvals, reviews, key);
  }

  /** Centipawns → winning chances in % (Lichess's curve). */
  static double winChance(int cp) {
    return 50 + 50 * (2 / (1 + Math.exp(-0.00368208 * cp)) - 1);
  }

  static String classify(double drop, boolean isBest) {
    if (drop >= 30) return "blunder";
    if (drop >= 20) return "mistake";
    if (drop >= 10) return "inaccuracy";
    if (isBest) return "best";
    if (drop < 2) return "excellent";
    return "good";
  }

  private static String symbol(String cls) {
    return switch (cls) {
      case "blunder" -> "??";
      case "mistake" -> "?";
      case "inaccuracy" -> "?!";
      default -> null;
    };
  }

  private static double round(double sum, int n) {
    return n == 0 ? 0 : Math.round(sum / n * 10) / 10.0;
  }
}
