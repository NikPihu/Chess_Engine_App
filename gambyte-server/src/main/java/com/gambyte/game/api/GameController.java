package com.gambyte.game.api;

import com.gambyte.auth.AuthUser;
import com.gambyte.common.RateLimiter;
import com.gambyte.game.api.GameDtos.ChatRequest;
import com.gambyte.game.api.GameDtos.ComputerGameRequest;
import com.gambyte.game.api.GameDtos.GamePage;
import com.gambyte.game.api.GameDtos.GameView;
import com.gambyte.game.api.GameDtos.MoveRequest;
import com.gambyte.game.service.GameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for games. Moves can also be sent over WebSocket ({@code GameSocketController})
 * for lower latency; both go through {@link GameService}.
 */
@RestController
@Tag(name = "Games", description = "Play, watch and browse games")
public class GameController {

  private final GameService games;
  private final RateLimiter limiter;

  public GameController(GameService games, RateLimiter limiter) {
    this.games = games;
    this.limiter = limiter;
  }

  @Operation(summary = "Start a game against Gambyte Engine (levels 1-8)")
  @PostMapping("/api/games/computer")
  public GameView playComputer(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody ComputerGameRequest body) {
    limiter.check("new-computer-game", String.valueOf(me.id()), 30, Duration.ofMinutes(10));
    return games.createComputerGame(
        me.id(), body.level(), body.color(), body.initial(), body.increment(), body.fen());
  }

  @Operation(summary = "A game, live or finished (also the reconnect snapshot)")
  @GetMapping("/api/games/{id}")
  public GameView get(@PathVariable String id) {
    return games.view(id);
  }

  @Operation(summary = "Download a finished game as PGN")
  @GetMapping(value = "/api/games/{id}/pgn", produces = "application/x-chess-pgn")
  public ResponseEntity<String> pgn(@PathVariable String id) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/x-chess-pgn"))
        .header("Content-Disposition", "attachment; filename=\"gambyte-" + id + ".pgn\"")
        // A finished game never changes: let browsers and CDNs cache it.
        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
        .body(games.pgn(id));
  }

  @Operation(summary = "Play a move (UCI notation, e.g. e2e4 or e7e8q)")
  @PostMapping("/api/games/{id}/move")
  public Map<String, Integer> move(
      @AuthenticationPrincipal AuthUser me,
      @PathVariable String id,
      @Valid @RequestBody MoveRequest body) {
    limiter.check("move", String.valueOf(me.id()), 600, Duration.ofMinutes(1));
    int version = games.move(id, me.id(), body.uci(), body.lag() == null ? 0 : body.lag());
    return Map.of("version", version);
  }

  @PostMapping("/api/games/{id}/resign")
  public ResponseEntity<Void> resign(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
    games.resign(id, me.id());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/games/{id}/abort")
  public ResponseEntity<Void> abort(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
    games.abort(id, me.id());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Offer a draw, or accept the opponent's offer")
  @PostMapping("/api/games/{id}/draw")
  public ResponseEntity<Void> draw(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
    games.offerDraw(id, me.id());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/games/{id}/draw/decline")
  public ResponseEntity<Void> declineDraw(
      @AuthenticationPrincipal AuthUser me, @PathVariable String id) {
    games.declineDraw(id, me.id());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Take back your last move (casual games vs the computer only)")
  @PostMapping("/api/games/{id}/takeback")
  public GameView takeback(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
    return games.takeback(id, me.id());
  }

  @Operation(summary = "Offer (or accept) a rematch; returns the new game id once both agree")
  @PostMapping("/api/games/{id}/rematch")
  public Map<String, String> rematch(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
    String next = games.rematch(id, me.id());
    return next == null ? Map.of() : Map.of("gameId", next);
  }

  @PostMapping("/api/games/{id}/chat")
  public ResponseEntity<Void> chat(
      @AuthenticationPrincipal AuthUser me,
      @PathVariable String id,
      @Valid @RequestBody ChatRequest body) {
    limiter.check("chat", String.valueOf(me.id()), 20, Duration.ofMinutes(1));
    games.chat(id, me.id(), body.text());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Your running games (to resume after closing the tab)")
  @GetMapping("/api/me/games/ongoing")
  public List<GameView> ongoing(@AuthenticationPrincipal AuthUser me) {
    return games.ongoing(me.id());
  }

  @Operation(summary = "A player's finished games, newest first")
  @GetMapping("/api/users/{username}/games")
  public GamePage archive(
      @PathVariable String username,
      @RequestParam(required = false) Instant before,
      @RequestParam(defaultValue = "20") int limit) {
    return games.archive(username, before, limit);
  }
}
