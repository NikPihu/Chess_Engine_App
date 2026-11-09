package com.gambyte.lobby;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gambyte.common.ApiException;
import com.gambyte.common.Tokens;
import com.gambyte.game.live.LiveGame;
import com.gambyte.game.service.GameEvents;
import com.gambyte.game.service.GameService;
import com.gambyte.user.domain.User;
import com.gambyte.user.service.UserService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import phase1.FenParser;

/**
 * Direct challenges: "play me, 10+0, I take white". A challenge is either addressed to one player
 * (they get a notification; bots get it on their event stream) or open (anyone with the link may
 * accept). Challenges live in Redis with a 20-minute expiry, so forgotten ones clean themselves up.
 */
@Service
public class ChallengeService {

  private static final String KEY = "gambyte:challenge:";
  private static final String INBOX = "gambyte:challenges-to:";
  private static final Duration TTL = Duration.ofMinutes(20);

  /** A pending challenge (stored in Redis as JSON). */
  public record Challenge(
      String id,
      long challengerId,
      String challenger,
      Long destId,
      String dest,
      int initial,
      int increment,
      boolean rated,
      String color,
      long createdAt) {}

  /** Published when a challenge is created, so the bot API can stream it to bot accounts. */
  public record ChallengeCreated(Challenge challenge) {}

  /** Published when a challenge is declined or cancelled. */
  public record ChallengeGone(Challenge challenge, String reason) {}

  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final UserService users;
  private final GameService games;
  private final GameEvents events;
  private final ApplicationEventPublisher spring;

  public ChallengeService(
      StringRedisTemplate redis,
      ObjectMapper json,
      UserService users,
      GameService games,
      GameEvents events,
      ApplicationEventPublisher spring) {
    this.redis = redis;
    this.json = json;
    this.users = users;
    this.games = games;
    this.events = events;
    this.spring = spring;
  }

  /**
   * Creates a challenge. Challenging Gambyte Engine starts the game right away (the engine always
   * accepts); then the returned challenge carries the game id in {@code id}.
   *
   * @param level engine level when challenging Gambyte Engine (1-8, default 8)
   */
  public Map<String, String> create(
      long challengerId, String destUsername, int initial, int increment, boolean rated,
      String color, Integer level) {
    User me = users.get(challengerId);
    User dest = null;
    if (destUsername != null && !destUsername.isBlank()) {
      dest = users.getByUsername(destUsername);
      if (dest.getId().equals(me.getId())) {
        throw ApiException.badRequest("self_challenge", "You cannot challenge yourself");
      }
      if (dest.isBanned()) throw ApiException.notFound("user");
    }
    boolean untimed = initial == 0 && increment == 0;
    if (untimed) rated = false; // unlimited games never count for rating

    if (dest != null && User.ENGINE_USERNAME.equals(dest.getUsername())) {
      boolean meWhite = pickWhite(color);
      LiveGame g = games.start(meWhite ? me.getId() : dest.getId(), meWhite ? dest.getId() : me.getId(),
          initial, increment, false, "computer", null, FenParser.START_POSITION, level == null ? 8 : level);
      return Map.of("id", g.id(), "gameId", g.id(), "url", "/game/" + g.id());
    }

    Challenge c = new Challenge(Tokens.shortId(8), me.getId(), me.getUsername(),
        dest == null ? null : dest.getId(), dest == null ? null : dest.getUsername(),
        initial, increment, rated, color == null ? "random" : color, System.currentTimeMillis());
    save(c);
    if (dest != null) {
      redis.opsForSet().add(INBOX + dest.getId(), c.id());
      redis.expire(INBOX + dest.getId(), TTL);
      events.toUser(dest.getUsername(), "/queue/challenge", c);
      spring.publishEvent(new ChallengeCreated(c));
    }
    return Map.of("id", c.id(), "url", "/challenge/" + c.id());
  }

  public Challenge get(String id) {
    return find(id).orElseThrow(() -> ApiException.notFound("challenge"));
  }

  /** Accepts a challenge: starts the game and sends both players to it. */
  public String accept(String id, long userId) {
    Challenge c = get(id);
    if (c.destId() != null && c.destId() != userId) {
      throw ApiException.forbidden("not_for_you", "This challenge is for someone else");
    }
    if (c.challengerId() == userId) {
      throw ApiException.badRequest("own_challenge", "You cannot accept your own challenge");
    }
    // Delete first: if two people click an open link at once, only one wins the race.
    if (!Boolean.TRUE.equals(redis.delete(KEY + id))) throw ApiException.notFound("challenge");
    if (c.destId() != null) redis.opsForSet().remove(INBOX + c.destId(), id);

    boolean challengerWhite = pickWhite(c.color());
    long white = challengerWhite ? c.challengerId() : userId;
    long black = challengerWhite ? userId : c.challengerId();
    LiveGame g = games.start(white, black, c.initial(), c.increment(), c.rated(),
        c.destId() != null && users.get(c.destId()).isBot() ? "bot" : "friend", null,
        FenParser.START_POSITION, null);
    Map<String, String> payload = Map.of("gameId", g.id());
    events.toUser(g.white().username(), "/queue/match", payload);
    events.toUser(g.black().username(), "/queue/match", payload);
    return g.id();
  }

  public void decline(String id, long userId) {
    Challenge c = get(id);
    if (c.destId() == null || c.destId() != userId) throw ApiException.forbidden("not_for_you", "Not your challenge");
    remove(c);
    events.toUser(c.challenger(), "/queue/challenge-declined", Map.of("id", id, "by", c.dest()));
    spring.publishEvent(new ChallengeGone(c, "declined"));
  }

  public void cancel(String id, long userId) {
    find(id).filter(c -> c.challengerId() == userId).ifPresent(c -> {
      remove(c);
      spring.publishEvent(new ChallengeGone(c, "cancelled"));
    });
  }

  /** Challenges waiting for this user to answer. */
  public List<Challenge> incoming(long userId) {
    List<Challenge> out = new ArrayList<>();
    var ids = redis.opsForSet().members(INBOX + userId);
    if (ids == null) return out;
    for (String id : ids) {
      find(id).ifPresentOrElse(out::add, () -> redis.opsForSet().remove(INBOX + userId, id));
    }
    return out;
  }

  private void save(Challenge c) {
    try {
      redis.opsForValue().set(KEY + c.id(), json.writeValueAsString(c), TTL);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Optional<Challenge> find(String id) {
    String raw = redis.opsForValue().get(KEY + id);
    if (raw == null) return Optional.empty();
    try {
      return Optional.of(json.readValue(raw, Challenge.class));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private void remove(Challenge c) {
    redis.delete(KEY + c.id());
    if (c.destId() != null) redis.opsForSet().remove(INBOX + c.destId(), c.id());
  }

  private static boolean pickWhite(String color) {
    if ("white".equals(color)) return true;
    if ("black".equals(color)) return false;
    return ThreadLocalRandom.current().nextBoolean();
  }
}
