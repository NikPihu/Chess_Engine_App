package com.gambyte.lobby;

import com.gambyte.auth.AuthUser;
import com.gambyte.common.RateLimiter;
import com.gambyte.game.service.GameService;
import com.gambyte.lobby.ChallengeService.Challenge;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Quick pairing (seeks), lobby numbers, and challenges. */
@RestController
@Tag(name = "Lobby", description = "Find opponents: quick pairing and challenges")
public class LobbyController {

  private final MatchmakingService matchmaking;
  private final ChallengeService challenges;
  private final GameService games;
  private final SimpUserRegistry connectedUsers;
  private final RateLimiter limiter;

  public LobbyController(
      MatchmakingService matchmaking,
      ChallengeService challenges,
      GameService games,
      SimpUserRegistry connectedUsers,
      RateLimiter limiter) {
    this.matchmaking = matchmaking;
    this.challenges = challenges;
    this.games = games;
    this.connectedUsers = connectedUsers;
    this.limiter = limiter;
  }

  public record SeekRequest(@Min(0) @Max(3600) int initial, @Min(0) @Max(60) int increment, boolean rated) {}

  public record ChallengeRequest(
      String username,
      @Min(0) @Max(10800) int initial,
      @Min(0) @Max(180) int increment,
      boolean rated,
      @Pattern(regexp = "white|black|random") String color,
      @Min(1) @Max(8) Integer level) {}

  @Operation(summary = "Join a quick-pairing pool; the game id arrives on /user/queue/match")
  @PostMapping("/api/lobby/seek")
  public Map<String, String> seek(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SeekRequest body) {
    limiter.check("seek", String.valueOf(me.id()), 30, Duration.ofMinutes(1));
    matchmaking.seek(me.id(), body.initial(), body.increment(), body.rated());
    return Map.of("status", "waiting");
  }

  @Operation(summary = "Leave the pool")
  @DeleteMapping("/api/lobby/seek")
  public ResponseEntity<Void> cancelSeek(@AuthenticationPrincipal AuthUser me) {
    matchmaking.cancel(me.id());
    return ResponseEntity.noContent().build();
  }

  public record LobbyStats(int online, int playing, Map<String, Integer> waiting) {}

  @Operation(summary = "Players online, games running, players waiting per pool")
  @GetMapping("/api/lobby/stats")
  public LobbyStats stats() {
    return new LobbyStats(connectedUsers.getUserCount(), games.runningCount() * 2, matchmaking.waitingCounts());
  }

  @Operation(summary = "Challenge a player (or create an open link when username is empty)")
  @PostMapping("/api/challenges")
  public Map<String, String> challenge(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChallengeRequest body) {
    limiter.check("challenge", String.valueOf(me.id()), 20, Duration.ofMinutes(10));
    return challenges.create(me.id(), body.username(), body.initial(), body.increment(),
        body.rated(), body.color(), body.level());
  }

  @GetMapping("/api/challenges/{id}")
  public Challenge getChallenge(@PathVariable String id) {
    return challenges.get(id);
  }

  @Operation(summary = "Challenges waiting for your answer")
  @GetMapping("/api/challenges")
  public List<Challenge> incoming(@AuthenticationPrincipal AuthUser me) {
    return challenges.incoming(me.id());
  }

  @PostMapping("/api/challenges/{id}/accept")
  public Map<String, String> accept(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
    return Map.of("gameId", challenges.accept(id, me.id()));
  }

  @PostMapping("/api/challenges/{id}/decline")
  public ResponseEntity<Void> decline(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
    challenges.decline(id, me.id());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/api/challenges/{id}")
  public ResponseEntity<Void> cancel(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
    challenges.cancel(id, me.id());
    return ResponseEntity.noContent().build();
  }
}
