package com.gambyte.social;

import com.gambyte.auth.AuthUser;
import com.gambyte.common.RateLimiter;
import com.gambyte.game.api.GameDtos.GameView;
import com.gambyte.game.service.GameService;
import com.gambyte.social.SocialService.Friend;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Follows, friends, notifications, reports and Gambyte TV. */
@RestController
@Tag(name = "Social", description = "Friends, notifications, reports, TV")
public class SocialController {

  private final SocialService social;
  private final NotificationService notifications;
  private final GameService games;
  private final RateLimiter limiter;

  public SocialController(
      SocialService social, NotificationService notifications, GameService games, RateLimiter limiter) {
    this.social = social;
    this.notifications = notifications;
    this.games = games;
    this.limiter = limiter;
  }

  @GetMapping("/api/social/follow/{username}")
  public Map<String, Boolean> isFollowing(@AuthenticationPrincipal AuthUser me, @PathVariable String username) {
    return Map.of("following", social.isFollowing(me.id(), username));
  }

  @PostMapping("/api/social/follow/{username}")
  public ResponseEntity<Void> follow(@AuthenticationPrincipal AuthUser me, @PathVariable String username) {
    limiter.check("follow", String.valueOf(me.id()), 60, Duration.ofHours(1));
    social.follow(me.id(), username);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/api/social/follow/{username}")
  public ResponseEntity<Void> unfollow(@AuthenticationPrincipal AuthUser me, @PathVariable String username) {
    social.unfollow(me.id(), username);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "People you follow (mutual = friends), online first")
  @GetMapping("/api/social/following")
  public List<Friend> following(@AuthenticationPrincipal AuthUser me) {
    return social.following(me.id());
  }

  @GetMapping("/api/notifications")
  public Map<String, Object> notifications(@AuthenticationPrincipal AuthUser me) {
    return notifications.summary(me.id());
  }

  @PostMapping("/api/notifications/read")
  public ResponseEntity<Void> markRead(@AuthenticationPrincipal AuthUser me) {
    notifications.markAllRead(me.id());
    return ResponseEntity.noContent().build();
  }

  public record ReportRequest(@NotBlank String reason, @Size(max = 1000) String text, String gameId) {}

  @Operation(summary = "Report a player (cheating, abuse, other). Moderators review every report.")
  @PostMapping("/api/users/{username}/report")
  public ResponseEntity<Void> report(
      @AuthenticationPrincipal AuthUser me, @PathVariable String username, @Valid @RequestBody ReportRequest body) {
    limiter.check("report", String.valueOf(me.id()), 10, Duration.ofHours(1));
    social.report(me.id(), username, body.reason(), body.text(), body.gameId());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Gambyte TV: the highest-rated games being played right now")
  @GetMapping("/api/tv")
  public List<GameView> tv() {
    return games.topLive(12);
  }
}
