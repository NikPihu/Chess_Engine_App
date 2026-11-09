package com.gambyte.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gambyte.game.api.GameDtos.GameView;
import com.gambyte.game.api.GameDtos.PlayerView;
import com.gambyte.game.service.GameEvents.GameChanged;
import com.gambyte.game.service.GameEvents.GameFinished;
import com.gambyte.game.service.GameEvents.GameStarted;
import com.gambyte.game.service.GameService;
import com.gambyte.lobby.ChallengeService.Challenge;
import com.gambyte.lobby.ChallengeService.ChallengeCreated;
import com.gambyte.lobby.ChallengeService.ChallengeGone;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import phase1.FenParser;

/**
 * The streaming side of the bot API, in the same shape as Lichess's so existing bot clients (like
 * lichess-bot) work by changing the server URL.
 *
 * <p>Streams are "NDJSON": a long-lived HTTP response that sends one JSON object per line as
 * things happen. An empty line every few seconds keeps proxies from closing idle connections.
 *
 * <ul>
 *   <li>{@code /api/stream/event} (per user): challenges, game starts and finishes
 *   <li>{@code /api/bot/game/stream/{id}} (per game): "gameFull" first, then "gameState" after
 *       every change
 * </ul>
 */
@Service
public class BotStreamService {

  private static final MediaType NDJSON = MediaType.parseMediaType("application/x-ndjson");

  private final Map<Long, Set<ResponseBodyEmitter>> eventStreams = new ConcurrentHashMap<>();
  private final Map<String, Set<ResponseBodyEmitter>> gameStreams = new ConcurrentHashMap<>();
  private final GameService games;
  private final ObjectMapper json;

  public BotStreamService(GameService games, ObjectMapper json) {
    this.games = games;
    this.json = json;
  }

  // ------------------------------------------------------------------ opening streams

  public ResponseBodyEmitter openEventStream(long userId) {
    ResponseBodyEmitter emitter = register(eventStreams, userId);
    // Tell a (re)connecting bot about games it is already playing.
    for (GameView g : games.ongoing(userId)) send(emitter, gameStartEvent("gameStart", g.id()));
    return emitter;
  }

  public ResponseBodyEmitter openGameStream(String gameId) {
    GameView view = games.view(gameId);
    ResponseBodyEmitter emitter = register(gameStreams, gameId);
    send(emitter, gameFull(view));
    if (!"STARTED".equals(view.status())) emitter.complete();
    return emitter;
  }

  private <K> ResponseBodyEmitter register(Map<K, Set<ResponseBodyEmitter>> map, K key) {
    ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L); // never time out
    Set<ResponseBodyEmitter> set = map.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>());
    set.add(emitter);
    Runnable remove = () -> set.remove(emitter);
    emitter.onCompletion(remove);
    emitter.onTimeout(remove);
    emitter.onError(e -> remove.run());
    return emitter;
  }

  // ------------------------------------------------------------------ reacting to the server

  @EventListener
  public void onGameStarted(GameStarted e) {
    for (long user : List.of(e.whiteId(), e.blackId())) {
      broadcast(eventStreams.get(user), gameStartEvent("gameStart", e.gameId()));
    }
  }

  @EventListener
  public void onGameChanged(GameChanged e) {
    Set<ResponseBodyEmitter> streams = gameStreams.get(e.gameId());
    if (streams == null || streams.isEmpty()) return;
    if ("chat".equals(e.event().t())) {
      broadcast(streams, Map.of("type", "chatLine", "username", e.event().by(), "text",
          e.event().text(), "room", "player"));
      return;
    }
    broadcast(streams, gameState(games.view(e.gameId())));
  }

  @EventListener
  public void onGameFinished(GameFinished e) {
    for (long user : List.of(e.whiteId(), e.blackId())) {
      broadcast(eventStreams.get(user), gameStartEvent("gameFinish", e.gameId()));
    }
    Set<ResponseBodyEmitter> streams = gameStreams.remove(e.gameId());
    if (streams != null) {
      broadcast(streams, gameState(games.view(e.gameId())));
      streams.forEach(ResponseBodyEmitter::complete);
    }
  }

  @EventListener
  public void onChallenge(ChallengeCreated e) {
    if (e.challenge().destId() == null) return;
    broadcast(eventStreams.get(e.challenge().destId()), Map.of("type", "challenge", "challenge", challengeJson(e.challenge())));
  }

  @EventListener
  public void onChallengeGone(ChallengeGone e) {
    Challenge c = e.challenge();
    String type = "declined".equals(e.reason()) ? "challengeDeclined" : "challengeCanceled";
    Map<String, Object> payload = Map.of("type", type, "challenge", challengeJson(c));
    broadcast(eventStreams.get(c.challengerId()), payload);
    if (c.destId() != null) broadcast(eventStreams.get(c.destId()), payload);
  }

  /** Keep-alive: an empty line every 6 seconds on every open stream. */
  @Scheduled(fixedDelay = 6_000)
  public void keepAlive() {
    for (var set : eventStreams.values()) set.forEach(this::newline);
    for (var set : gameStreams.values()) set.forEach(this::newline);
  }

  // ------------------------------------------------------------------ JSON shapes (Lichess-like)

  private static Map<String, Object> gameStartEvent(String type, String gameId) {
    return Map.of("type", type, "game", Map.of("id", gameId, "gameId", gameId));
  }

  private Map<String, Object> gameFull(GameView g) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", "gameFull");
    m.put("id", g.id());
    m.put("rated", g.rated());
    m.put("variant", Map.of("key", "standard", "name", "Standard"));
    m.put("speed", g.perf());
    m.put("perf", Map.of("name", g.perf()));
    m.put("clock", Map.of("initial", g.clock().initial(), "increment", g.clock().increment()));
    m.put("createdAt", g.createdAt().toEpochMilli());
    m.put("white", player(g.white()));
    m.put("black", player(g.black()));
    m.put("initialFen", FenParser.START_POSITION.equals(g.startFen()) ? "startpos" : g.startFen());
    m.put("state", gameState(g));
    return m;
  }

  private static Map<String, Object> gameState(GameView g) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", "gameState");
    m.put("moves", String.join(" ", g.moves()));
    long untimed = 24L * 3600 * 1000; // "unlimited" reported as a day, so bots don't panic
    boolean noClock = g.clock().initial() == 0 && g.clock().increment() == 0;
    m.put("wtime", noClock ? untimed : g.clock().white());
    m.put("btime", noClock ? untimed : g.clock().black());
    m.put("winc", g.clock().increment());
    m.put("binc", g.clock().increment());
    m.put("status", lichessStatus(g.status()));
    if (g.winner() != null) m.put("winner", g.winner());
    m.put("wdraw", "white".equals(g.drawOffer()));
    m.put("bdraw", "black".equals(g.drawOffer()));
    return m;
  }

  private static Map<String, Object> player(PlayerView p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", p.username().toLowerCase());
    m.put("name", p.username());
    if (p.title() != null) m.put("title", p.title());
    if (p.rating() != null) m.put("rating", p.rating());
    return m;
  }

  private static Map<String, Object> challengeJson(Challenge c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.id());
    m.put("url", "/challenge/" + c.id());
    m.put("status", "created");
    m.put("challenger", Map.of("id", c.challenger().toLowerCase(), "name", c.challenger()));
    if (c.dest() != null) m.put("destUser", Map.of("id", c.dest().toLowerCase(), "name", c.dest()));
    m.put("variant", Map.of("key", "standard", "name", "Standard"));
    m.put("rated", c.rated());
    m.put("speed", com.gambyte.user.domain.Perf.forTimeControl(c.initial(), c.increment()).key());
    m.put("timeControl", c.initial() == 0 && c.increment() == 0
        ? Map.of("type", "unlimited")
        : Map.of("type", "clock", "limit", c.initial(), "increment", c.increment()));
    m.put("color", c.color());
    m.put("perf", Map.of("name", com.gambyte.user.domain.Perf.forTimeControl(c.initial(), c.increment()).key()));
    return m;
  }

  /** Our statuses → Lichess's names ("mate", "resign", "outoftime", ...). */
  private static String lichessStatus(String status) {
    return switch (status) {
      case "STARTED" -> "started";
      case "ABORTED" -> "aborted";
      case "MATE" -> "mate";
      case "RESIGN" -> "resign";
      case "STALEMATE" -> "stalemate";
      case "TIMEOUT" -> "outoftime";
      case "DRAW" -> "draw";
      case "ABANDONED" -> "timeout";
      case "CHEAT" -> "cheat";
      default -> status.toLowerCase();
    };
  }

  // ------------------------------------------------------------------ writing

  private void broadcast(Set<ResponseBodyEmitter> emitters, Object payload) {
    if (emitters != null) emitters.forEach(e -> send(e, payload));
  }

  private void send(ResponseBodyEmitter emitter, Object payload) {
    try {
      emitter.send(json.writeValueAsString(payload) + "\n", NDJSON);
    } catch (IOException | IllegalStateException e) {
      emitter.completeWithError(e);
    }
  }

  private void newline(ResponseBodyEmitter emitter) {
    try {
      emitter.send("\n", NDJSON);
    } catch (IOException | IllegalStateException e) {
      emitter.completeWithError(e);
    }
  }
}
