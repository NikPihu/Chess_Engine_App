package com.gambyte.bot;

import com.gambyte.auth.AuthUser;
import com.gambyte.common.ApiException;
import com.gambyte.common.RateLimiter;
import com.gambyte.game.repo.GameRepository;
import com.gambyte.game.service.GameService;
import com.gambyte.lobby.ChallengeService;
import com.gambyte.user.api.UserDtos.Profile;
import com.gambyte.user.domain.User;
import com.gambyte.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * The Bot API: lets anyone run a chess program on their own machine and play on Gambyte as a BOT
 * account. Paths and JSON follow Lichess's API, so lichess-bot works with {@code url:
 * https://<gambyte host>/} and a Gambyte API token.
 *
 * <p>Gambyte never runs uploaded programs on its servers (unsafe and expensive); bots connect
 * from outside with a token, exactly like on Lichess.
 */
@RestController
@Tag(name = "Bot API", description = "Lichess-compatible endpoints for bot accounts (Bearer token)")
public class BotApiController {

  private final UserService users;
  private final GameService games;
  private final GameRepository gameRepo;
  private final ChallengeService challenges;
  private final BotStreamService streams;
  private final RateLimiter limiter;

  public BotApiController(
      UserService users,
      GameService games,
      GameRepository gameRepo,
      ChallengeService challenges,
      BotStreamService streams,
      RateLimiter limiter) {
    this.users = users;
    this.games = games;
    this.gameRepo = gameRepo;
    this.challenges = challenges;
    this.streams = streams;
    this.limiter = limiter;
  }

  @Operation(summary = "The token's account (Lichess: GET /api/account)")
  @GetMapping("/api/account")
  public Map<String, Object> account(@AuthenticationPrincipal AuthUser me) {
    Profile p = users.profile(me.username());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", p.username().toLowerCase());
    out.put("username", p.username());
    if (p.title() != null) out.put("title", p.title());
    Map<String, Object> perfs = new LinkedHashMap<>();
    p.ratings().forEach((perf, r) -> perfs.put(perf, Map.of("rating", r.rating(), "games", r.games(), "prov", r.provisional())));
    out.put("perfs", perfs);
    return out;
  }

  @Operation(summary = "Turn this account into a BOT account (only before it has played)")
  @PostMapping("/api/bot/account/upgrade")
  public Map<String, Boolean> upgrade(@AuthenticationPrincipal AuthUser me) {
    if (gameRepo.countFinished(me.id()) > 0) {
      throw ApiException.badRequest("has_games", "Only accounts that have never played can become bots");
    }
    users.makeBot(me.id());
    return Map.of("ok", true);
  }

  @Operation(summary = "Stream of challenges and game starts for this account (NDJSON)")
  @GetMapping(value = "/api/stream/event", produces = "application/x-ndjson")
  public ResponseBodyEmitter eventStream(@AuthenticationPrincipal AuthUser me) {
    return streams.openEventStream(me.id());
  }

  @Operation(summary = "Stream of one game's state (NDJSON): gameFull, then gameState updates")
  @GetMapping(value = {"/api/bot/game/stream/{id}", "/api/board/game/stream/{id}"}, produces = "application/x-ndjson")
  public ResponseBodyEmitter gameStream(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
    return streams.openGameStream(id);
  }

  @Operation(summary = "Play a move (UCI)")
  @PostMapping({"/api/bot/game/{id}/move/{uci}", "/api/board/game/{id}/move/{uci}"})
  public Map<String, Boolean> move(
      @AuthenticationPrincipal AuthUser me, @PathVariable String id, @PathVariable String uci) {
    limiter.check("bot-move", String.valueOf(me.id()), 600, Duration.ofMinutes(1));
    games.move(id, me.id(), uci, 0);
    return Map.of("ok", true);
  }

  @PostMapping({"/api/bot/game/{id}/resign", "/api/board/game/{id}/resign"})
  public Map<String, Boolean> resign(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
    games.resign(id, me.id());
    return Map.of("ok", true);
  }

  @PostMapping({"/api/bot/game/{id}/abort", "/api/board/game/{id}/abort"})
  public Map<String, Boolean> abort(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
    games.abort(id, me.id());
    return Map.of("ok", true);
  }

  @PostMapping({"/api/bot/game/{id}/chat", "/api/board/game/{id}/chat"})
  public Map<String, Boolean> chat(
      @AuthenticationPrincipal AuthUser me, @PathVariable String id, @RequestParam String text) {
    games.chat(id, me.id(), text.length() > 140 ? text.substring(0, 140) : text);
    return Map.of("ok", true);
  }

  @PostMapping("/api/challenge/{id}/accept")
  public Map<String, Boolean> accept(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
    challenges.accept(id, me.id());
    return Map.of("ok", true);
  }

  @PostMapping("/api/challenge/{id}/decline")
  public Map<String, Boolean> decline(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
    challenges.decline(id, me.id());
    return Map.of("ok", true);
  }

  /** Lichess-style challenge creation (form parameters, as lichess-bot's matchmaking sends). */
  @PostMapping(value = "/api/challenge/{username}", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.ALL_VALUE})
  public ResponseEntity<Map<String, Object>> create(
      @AuthenticationPrincipal AuthUser me,
      @PathVariable String username,
      @RequestParam(name = "rated", defaultValue = "false") boolean rated,
      @RequestParam(name = "clock.limit", defaultValue = "0") int limit,
      @RequestParam(name = "clock.increment", defaultValue = "0") int increment,
      @RequestParam(name = "color", defaultValue = "random") String color) {
    limiter.check("challenge", String.valueOf(me.id()), 20, Duration.ofMinutes(10));
    var created = challenges.create(me.id(), username, limit, increment, rated, color, null);
    return ResponseEntity.ok(Map.of("challenge", Map.of("id", created.get("id"), "url", created.get("url"))));
  }

  @Operation(summary = "Online bots (for bot matchmaking)")
  @GetMapping(value = "/api/bot/online", produces = "application/x-ndjson")
  public String onlineBots() {
    User engine = users.getByUsername(User.ENGINE_USERNAME);
    return "{\"id\":\"" + engine.getUsername().toLowerCase() + "\",\"username\":\""
        + engine.getUsername() + "\",\"title\":\"BOT\"}\n";
  }
}
