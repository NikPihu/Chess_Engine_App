package com.gambyte.analysis;

import com.gambyte.analysis.AnalysisService.Eval;
import com.gambyte.analysis.ReviewService.ReviewStatus;
import com.gambyte.auth.AuthUser;
import com.gambyte.common.ApiException;
import com.gambyte.common.ClientIp;
import com.gambyte.common.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Engine analysis, Game Review and the coach. */
@RestController
@Tag(name = "Analysis", description = "Engine analysis, game review and coaching")
public class AnalysisController {

  private final AnalysisService analysis;
  private final ReviewService reviews;
  private final CoachChatService coach;
  private final RateLimiter limiter;

  public AnalysisController(
      AnalysisService analysis, ReviewService reviews, CoachChatService coach, RateLimiter limiter) {
    this.analysis = analysis;
    this.reviews = reviews;
    this.coach = coach;
    this.limiter = limiter;
  }

  @Operation(summary = "Evaluate a position (analysis board)")
  @GetMapping("/api/analysis/eval")
  public Eval eval(
      @RequestParam String fen,
      @AuthenticationPrincipal AuthUser me,
      HttpServletRequest request) {
    String who = me != null ? "u" + me.id() : ClientIp.of(request);
    limiter.check("eval", who, me != null ? 120 : 30, Duration.ofMinutes(1));
    return analysis.evaluate(fen, me != null ? 500_000 : 150_000);
  }

  @Operation(summary = "Request a Game Review (queued; poll GET for the result)")
  @PostMapping("/api/games/{id}/review")
  public ReviewStatus requestReview(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
    return reviews.request(id, me.id());
  }

  @Operation(summary = "A game's review: QUEUED (with queue position), RUNNING, DONE (with result) or FAILED")
  @GetMapping("/api/games/{id}/review")
  public ReviewStatus review(@PathVariable String id) {
    ReviewStatus s = reviews.status(id);
    if (s == null) throw ApiException.notFound("review");
    return s;
  }

  public record AskRequest(Integer ply, @NotBlank @Size(max = 300) String question) {}

  @Operation(summary = "Ask the AI coach about a reviewed game (Premium, if enabled)")
  @PostMapping("/api/games/{id}/coach")
  public Map<String, String> ask(
      @AuthenticationPrincipal AuthUser me, @PathVariable String id, @Valid @RequestBody AskRequest body) {
    limiter.check("coach", String.valueOf(me.id()), 20, Duration.ofHours(1));
    return Map.of("answer", coach.ask(me.id(), id, body.ply(), body.question()));
  }

  @GetMapping("/api/coach/status")
  public Map<String, Boolean> coachStatus() {
    return Map.of("enabled", coach.enabled());
  }
}
