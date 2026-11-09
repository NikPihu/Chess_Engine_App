package com.gambyte.tournament;

import com.gambyte.common.ApiException;
import com.gambyte.game.live.LiveGame;
import com.gambyte.game.service.GameEvents;
import com.gambyte.game.service.GameEvents.GameFinished;
import com.gambyte.game.service.GameService;
import com.gambyte.rating.RatingService;
import com.gambyte.user.domain.Perf;
import com.gambyte.user.service.PresenceService;
import com.gambyte.user.service.UserService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phase1.FenParser;

/**
 * Arena tournaments (the Lichess format): for a fixed time, players are paired again as soon as
 * their previous game ends. Scoring: win 2, draw 1, loss 0; after two wins in a row a player is "on
 * fire" and wins count double until they fail to win. Most points when the clock runs out wins.
 *
 * <p>Pairing runs every 3 seconds: free players (joined, not withdrawn, online, not in a game) are
 * sorted by score and paired with a neighbour, avoiding an immediate rematch when possible.
 *
 * <p>An hourly 3+0 blitz arena is created automatically, so there is always something to join.
 */
@Service
public class TournamentService {

  public record Tournament(long id, String name, int initial, int increment, boolean rated,
      Instant startsAt, int minutes, String status, Long winnerId, int players) {}

  public record Standing(int rank, long userId, String username, int score, int games, boolean onFire, int rating, boolean withdrawn) {}

  public record Detail(Tournament tournament, List<Standing> standings, Instant endsAt, boolean joined) {}

  private final JdbcTemplate jdbc;
  private final GameService games;
  private final GameEvents events;
  private final UserService users;
  private final RatingService ratings;
  private final PresenceService presence;

  public TournamentService(JdbcTemplate jdbc, GameService games, GameEvents events, UserService users,
      RatingService ratings, PresenceService presence) {
    this.jdbc = jdbc;
    this.games = games;
    this.events = events;
    this.users = users;
    this.ratings = ratings;
    this.presence = presence;
  }

  // ------------------------------------------------------------------ queries

  public List<Tournament> list() {
    return jdbc.query("""
        SELECT t.*, (SELECT count(*) FROM tournament_players p WHERE p.tournament_id = t.id) AS players
        FROM tournaments t
        WHERE t.status <> 'FINISHED' OR t.starts_at > now() - interval '1 day'
        ORDER BY CASE t.status WHEN 'STARTED' THEN 0 WHEN 'CREATED' THEN 1 ELSE 2 END, t.starts_at
        LIMIT 30""", (rs, i) -> tournament(rs));
  }

  public Detail detail(long id, Long userId) {
    Tournament t = get(id);
    List<Standing> standings = new ArrayList<>();
    jdbc.query("""
        SELECT p.*, u.username FROM tournament_players p JOIN users u ON u.id = p.user_id
        WHERE p.tournament_id = ? ORDER BY p.score DESC, p.rating DESC""",
        rs -> {
          standings.add(new Standing(standings.size() + 1, rs.getLong("user_id"), rs.getString("username"),
              rs.getInt("score"), rs.getInt("games"), rs.getInt("wins_in_row") >= 2, rs.getInt("rating"),
              rs.getBoolean("withdrawn")));
        }, id);
    boolean joined = userId != null && standings.stream().anyMatch(s -> s.userId() == userId && !s.withdrawn());
    return new Detail(t, standings, t.startsAt().plus(Duration.ofMinutes(t.minutes())), joined);
  }

  private Tournament get(long id) {
    List<Tournament> rows = jdbc.query("""
        SELECT t.*, (SELECT count(*) FROM tournament_players p WHERE p.tournament_id = t.id) AS players
        FROM tournaments t WHERE id = ?""", (rs, i) -> tournament(rs), id);
    if (rows.isEmpty()) throw ApiException.notFound("tournament");
    return rows.getFirst();
  }

  private static Tournament tournament(java.sql.ResultSet rs) throws java.sql.SQLException {
    long winner = rs.getLong("winner_id");
    return new Tournament(rs.getLong("id"), rs.getString("name"), rs.getInt("initial"), rs.getInt("increment"),
        rs.getBoolean("rated"), rs.getTimestamp("starts_at").toInstant(), rs.getInt("minutes"),
        rs.getString("status"), rs.wasNull() ? null : winner, rs.getInt("players"));
  }

  // ------------------------------------------------------------------ commands

  public long create(long userId, String name, int initial, int increment, boolean rated, int startsInMinutes, int minutes) {
    if (minutes < 10 || minutes > 180) throw ApiException.badRequest("bad_duration", "Duration must be 10-180 minutes");
    Long id = jdbc.queryForObject("""
        INSERT INTO tournaments (name, initial, increment, rated, starts_at, minutes, created_by)
        VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, name, initial, increment, rated,
        Timestamp.from(Instant.now().plus(Duration.ofMinutes(Math.max(0, startsInMinutes)))), minutes, userId);
    return id == null ? 0 : id;
  }

  public void join(long id, long userId) {
    Tournament t = get(id);
    if ("FINISHED".equals(t.status())) throw ApiException.badRequest("finished", "This tournament is over");
    if (users.get(userId).isBot()) throw ApiException.forbidden("no_bots", "Bots cannot join arenas");
    int rating = ratings.current(userId, Perf.forTimeControl(t.initial(), t.increment()));
    jdbc.update("""
        INSERT INTO tournament_players (tournament_id, user_id, rating) VALUES (?, ?, ?)
        ON CONFLICT (tournament_id, user_id) DO UPDATE SET withdrawn = FALSE""", id, userId, rating);
  }

  public void withdraw(long id, long userId) {
    jdbc.update("UPDATE tournament_players SET withdrawn = TRUE WHERE tournament_id = ? AND user_id = ?", id, userId);
  }

  // ------------------------------------------------------------------ the arena engine

  /** Starts due tournaments, ends expired ones, pairs free players (every 3 s). */
  @Scheduled(fixedDelay = 3_000)
  @Transactional
  public void tick() {
    jdbc.update("UPDATE tournaments SET status = 'STARTED' WHERE status = 'CREATED' AND starts_at <= now()");
    List<Long> ending = jdbc.queryForList(
        "SELECT id FROM tournaments WHERE status = 'STARTED' AND starts_at + minutes * interval '1 minute' <= now()",
        Long.class);
    for (long id : ending) {
      jdbc.update("""
          UPDATE tournaments SET status = 'FINISHED', winner_id = (
            SELECT user_id FROM tournament_players WHERE tournament_id = ? AND games > 0
            ORDER BY score DESC, rating DESC LIMIT 1) WHERE id = ?""", id, id);
    }
    for (long id : jdbc.queryForList("SELECT id FROM tournaments WHERE status = 'STARTED'", Long.class)) pair(id);
  }

  private void pair(long tournamentId) {
    Tournament t = get(tournamentId);
    // Don't start games that cannot finish before the end.
    Instant end = t.startsAt().plus(Duration.ofMinutes(t.minutes()));
    if (Instant.now().plusSeconds(Math.max(30, t.initial())).isAfter(end)) return;

    Set<Long> busy = new HashSet<>();
    for (var g : games.ongoingAll()) {
      busy.add(g.white().userId());
      busy.add(g.black().userId());
    }
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT user_id, score, last_opponent FROM tournament_players
        WHERE tournament_id = ? AND NOT withdrawn ORDER BY score DESC, rating DESC""", tournamentId);
    List<Long> free = new ArrayList<>();
    Map<Long, Long> lastOpp = new java.util.HashMap<>();
    for (var r : rows) {
      long uid = ((Number) r.get("user_id")).longValue();
      if (!busy.contains(uid) && presence.isOnline(uid)) free.add(uid);
      Object lo = r.get("last_opponent");
      if (lo != null) lastOpp.put(uid, ((Number) lo).longValue());
    }
    while (free.size() >= 2) {
      long a = free.removeFirst();
      int j = 0;
      while (j < free.size() - 1 && Long.valueOf(free.get(j)).equals(lastOpp.get(a))) j++;
      long b = free.remove(j);
      boolean aWhite = ThreadLocalRandom.current().nextBoolean();
      LiveGame g = games.start(aWhite ? a : b, aWhite ? b : a, t.initial(), t.increment(), t.rated(), "arena",
          tournamentId, FenParser.START_POSITION, null);
      jdbc.update("UPDATE tournament_players SET last_opponent = ? WHERE tournament_id = ? AND user_id = ?", b, tournamentId, a);
      jdbc.update("UPDATE tournament_players SET last_opponent = ? WHERE tournament_id = ? AND user_id = ?", a, tournamentId, b);
      Map<String, String> payload = Map.of("gameId", g.id());
      events.toUser(g.white().username(), "/queue/match", payload);
      events.toUser(g.black().username(), "/queue/match", payload);
    }
  }

  /** Scores a finished arena game. */
  @EventListener
  @Transactional
  public void onGameFinished(GameFinished e) {
    if (e.tournamentId() == null) return;
    score(e.tournamentId(), e.whiteId(), e.winner() == null ? 1 : "white".equals(e.winner()) ? 2 : 0);
    score(e.tournamentId(), e.blackId(), e.winner() == null ? 1 : "black".equals(e.winner()) ? 2 : 0);
  }

  private void score(long tournamentId, long userId, int points) {
    // "On fire" (2+ wins in a row) doubles the points of the next result.
    jdbc.update("""
        UPDATE tournament_players SET
          score = score + CASE WHEN wins_in_row >= 2 THEN ? * 2 ELSE ? END,
          games = games + 1,
          wins_in_row = CASE WHEN ? = 2 THEN wins_in_row + 1 ELSE 0 END
        WHERE tournament_id = ? AND user_id = ?""", points, points, points, tournamentId, userId);
  }

  /** Keeps one upcoming hourly blitz arena on the schedule. */
  @Scheduled(cron = "0 5 * * * *")
  public void scheduleHourly() {
    Integer upcoming = jdbc.queryForObject(
        "SELECT count(*) FROM tournaments WHERE created_by IS NULL AND status = 'CREATED'", Integer.class);
    if (upcoming != null && upcoming > 0) return;
    Instant next = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofHours(1));
    jdbc.update("INSERT INTO tournaments (name, initial, increment, rated, starts_at, minutes) VALUES (?, 180, 0, TRUE, ?, 57)",
        "Hourly Blitz Arena", Timestamp.from(next));
  }
}
