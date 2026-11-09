package com.gambyte.lobby;

import com.gambyte.common.ApiException;
import com.gambyte.game.live.LiveGame;
import com.gambyte.game.service.GameEvents;
import com.gambyte.game.service.GameService;
import com.gambyte.rating.RatingService;
import com.gambyte.user.domain.Perf;
import com.gambyte.user.domain.User;
import com.gambyte.user.service.UserService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import phase1.FenParser;

/**
 * Quick pairing ("seek a 3+2 game").
 *
 * <p><b>Data structure:</b> one Redis <em>sorted set</em> per pool (time control + rated), with
 * each waiting player stored as a member scored by their rating. Sorted sets keep members ordered
 * by score (a skip list inside Redis), so "players near rating R" is a cheap range query.
 *
 * <p><b>Pairing:</b> twice a second {@link #pair()} walks each pool in rating order and pairs
 * neighbours whose rating gap is within an <em>acceptable window</em> that <b>widens the longer
 * they wait</b> (±150 at first, +40 per second, up to ±1000): quick games for everyone, close
 * matches when possible.
 *
 * <p>Removing a pair uses {@code ZREM} and checks that both members were really removed, so a
 * player who cancelled at the same instant is never put into a game.
 */
@Service
public class MatchmakingService {

  private static final Logger log = LoggerFactory.getLogger(MatchmakingService.class);
  private static final String POOL_PREFIX = "gambyte:pool:";
  private static final String JOINED_PREFIX = "gambyte:seek-joined:";
  private static final String USER_POOL_PREFIX = "gambyte:seek-of:";
  private static final long MAX_WAIT_MS = 10 * 60_000; // seeks expire after 10 minutes

  private final StringRedisTemplate redis;
  private final GameService games;
  private final GameEvents events;
  private final RatingService ratings;
  private final UserService users;

  public MatchmakingService(
      StringRedisTemplate redis,
      GameService games,
      GameEvents events,
      RatingService ratings,
      UserService users) {
    this.redis = redis;
    this.games = games;
    this.events = events;
    this.ratings = ratings;
    this.users = users;
  }

  /** Allowed quick-pairing time controls (seconds). Anything else goes through a challenge. */
  private static final Set<String> POOLS =
      Set.of("60+0", "120+1", "180+0", "180+2", "300+0", "300+3", "600+0", "600+5", "900+10",
          "1800+0", "1800+20", "3600+0");

  /** Joins a pool. Leaves any other pool the user was in. */
  public void seek(long userId, int initial, int increment, boolean rated) {
    String tc = initial + "+" + increment;
    if (!POOLS.contains(tc)) throw ApiException.badRequest("bad_pool", "Unknown time control");
    User u = users.get(userId);
    if (u.isBot()) throw ApiException.forbidden("bots_cannot_seek", "Bots play by challenge only");
    if (!games.ongoing(userId).isEmpty()) {
      throw ApiException.conflict("already_playing", "Finish your current game first");
    }
    cancel(userId);
    String pool = POOL_PREFIX + tc + (rated ? ":r" : ":c");
    int rating = ratings.current(userId, Perf.forTimeControl(initial, increment));
    redis.opsForZSet().add(pool, String.valueOf(userId), rating);
    redis.opsForValue().set(JOINED_PREFIX + userId, String.valueOf(System.currentTimeMillis()));
    redis.opsForValue().set(USER_POOL_PREFIX + userId, pool);
  }

  public void cancel(long userId) {
    String pool = redis.opsForValue().get(USER_POOL_PREFIX + userId);
    if (pool != null) redis.opsForZSet().remove(pool, String.valueOf(userId));
    redis.delete(List.of(USER_POOL_PREFIX + userId, JOINED_PREFIX + userId));
  }

  /** How many players wait in each pool, for the lobby ("3+0 · 4 waiting"). */
  public Map<String, Integer> waitingCounts() {
    Map<String, Integer> out = new HashMap<>();
    for (String tc : POOLS) {
      long n = 0;
      for (String suffix : List.of(":r", ":c")) {
        Long size = redis.opsForZSet().size(POOL_PREFIX + tc + suffix);
        n += size == null ? 0 : size;
      }
      int initial = Integer.parseInt(tc.split("\\+")[0]);
      String label = (initial / 60) + "+" + tc.split("\\+")[1];
      if (n > 0) out.put(label, (int) n);
    }
    return out;
  }

  /** A browser closed its connection: take that user out of the pools. */
  @EventListener
  public void onDisconnect(SessionDisconnectEvent event) {
    if (event.getUser() instanceof org.springframework.security.core.Authentication auth
        && auth.getPrincipal() instanceof com.gambyte.auth.AuthUser user) {
      cancel(user.id());
    }
  }

  @Scheduled(fixedDelay = 500)
  public void pair() {
    long now = System.currentTimeMillis();
    for (String tc : POOLS) {
      for (String suffix : List.of(":r", ":c")) {
        try {
          pairPool(tc, suffix.equals(":r"), POOL_PREFIX + tc + suffix, now);
        } catch (RuntimeException e) {
          log.warn("Pairing failed in {}{}: {}", tc, suffix, e.getMessage());
        }
      }
    }
  }

  private void pairPool(String tc, boolean rated, String pool, long now) {
    Set<TypedTuple<String>> members = redis.opsForZSet().rangeWithScores(pool, 0, -1);
    if (members == null || members.size() < 2) {
      expireOld(pool, members, now);
      return;
    }
    List<TypedTuple<String>> list = new ArrayList<>(members); // sorted by rating
    int i = 0;
    while (i < list.size() - 1) {
      TypedTuple<String> a = list.get(i);
      TypedTuple<String> b = list.get(i + 1);
      long waitA = now - joinedAt(a.getValue(), now);
      long waitB = now - joinedAt(b.getValue(), now);
      double window = Math.min(1000, 150 + 40 * (Math.min(waitA, waitB) / 1000.0));
      if (b.getScore() - a.getScore() <= window && startGame(pool, tc, rated, a, b)) {
        i += 2;
      } else {
        i += 1;
      }
    }
    expireOld(pool, members, now);
  }

  private boolean startGame(
      String pool, String tc, boolean rated, TypedTuple<String> ta, TypedTuple<String> tb) {
    String a = ta.getValue();
    String b = tb.getValue();
    Long removed = redis.opsForZSet().remove(pool, a, b);
    if (removed == null || removed != 2) {
      // Someone cancelled at this very moment: put back whoever is still seeking.
      for (TypedTuple<String> t : List.of(ta, tb)) {
        if (Boolean.TRUE.equals(redis.hasKey(USER_POOL_PREFIX + t.getValue()))) {
          redis.opsForZSet().add(pool, t.getValue(), t.getScore());
        }
      }
      return false;
    }
    long idA = Long.parseLong(a);
    long idB = Long.parseLong(b);
    redis.delete(List.of(USER_POOL_PREFIX + a, USER_POOL_PREFIX + b, JOINED_PREFIX + a, JOINED_PREFIX + b));
    boolean aWhite = ThreadLocalRandom.current().nextBoolean();
    String[] parts = tc.split("\\+");
    LiveGame game =
        games.start(aWhite ? idA : idB, aWhite ? idB : idA, Integer.parseInt(parts[0]),
            Integer.parseInt(parts[1]), rated, "lobby", null, FenParser.START_POSITION, null);
    Map<String, String> payload = Map.of("gameId", game.id());
    events.toUser(game.white().username(), "/queue/match", payload);
    events.toUser(game.black().username(), "/queue/match", payload);
    return true;
  }

  private long joinedAt(String userId, long fallback) {
    String v = redis.opsForValue().get(JOINED_PREFIX + userId);
    return v == null ? fallback : Long.parseLong(v);
  }

  private void expireOld(String pool, Set<TypedTuple<String>> members, long now) {
    if (members == null) return;
    for (TypedTuple<String> m : members) {
      if (now - joinedAt(m.getValue(), now) > MAX_WAIT_MS) cancel(Long.parseLong(m.getValue()));
    }
  }
}
