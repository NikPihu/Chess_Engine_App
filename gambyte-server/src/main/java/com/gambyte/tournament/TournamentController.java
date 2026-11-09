package com.gambyte.tournament;

import com.gambyte.auth.AuthUser;
import com.gambyte.common.RateLimiter;
import com.gambyte.tournament.TournamentService.Detail;
import com.gambyte.tournament.TournamentService.Tournament;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Arena tournaments: list, details/standings, create, join, withdraw. */
@RestController
@Tag(name = "Tournaments", description = "Arena tournaments")
public class TournamentController {

  private final TournamentService tournaments;
  private final RateLimiter limiter;

  public TournamentController(TournamentService tournaments, RateLimiter limiter) {
    this.tournaments = tournaments;
    this.limiter = limiter;
  }

  @GetMapping("/api/tournaments")
  public List<Tournament> list() {
    return tournaments.list();
  }

  @GetMapping("/api/tournaments/{id}")
  public Detail detail(@PathVariable long id, @AuthenticationPrincipal AuthUser me) {
    return tournaments.detail(id, me == null ? null : me.id());
  }

  public record CreateRequest(
      @NotBlank @Size(max = 80) String name,
      @Min(30) @Max(3600) int initial,
      @Min(0) @Max(60) int increment,
      boolean rated,
      @Min(0) @Max(1440) int startsInMinutes,
      @Min(10) @Max(180) int minutes) {}

  @PostMapping("/api/tournaments")
  public Map<String, Long> create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateRequest body) {
    limiter.check("create-tournament", String.valueOf(me.id()), 5, Duration.ofDays(1));
    return Map.of("id", tournaments.create(me.id(), body.name(), body.initial(), body.increment(), body.rated(),
        body.startsInMinutes(), body.minutes()));
  }

  @PostMapping("/api/tournaments/{id}/join")
  public ResponseEntity<Void> join(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
    tournaments.join(id, me.id());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/tournaments/{id}/withdraw")
  public ResponseEntity<Void> withdraw(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
    tournaments.withdraw(id, me.id());
    return ResponseEntity.noContent().build();
  }
}
