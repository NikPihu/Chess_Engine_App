package com.gambyte.puzzle;

import com.gambyte.auth.AuthUser;
import com.gambyte.puzzle.PuzzleService.AttemptResult;
import com.gambyte.puzzle.PuzzleService.PuzzleView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Puzzles: next puzzle, daily puzzle, results, themes and Puzzle Rush. */
@RestController
@Validated
@Tag(name = "Puzzles", description = "Tactics training")
public class PuzzleController {

  private final PuzzleService puzzles;

  public PuzzleController(PuzzleService puzzles) {
    this.puzzles = puzzles;
  }

  @Operation(summary = "A puzzle at your level (visitors get ~1500)")
  @GetMapping("/api/puzzles/next")
  public PuzzleView next(
      @AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String theme) {
    return puzzles.next(me == null ? null : me.id(), theme);
  }

  @Operation(summary = "Today's puzzle (the same for everyone)")
  @GetMapping("/api/puzzles/daily")
  public PuzzleView daily() {
    return puzzles.daily();
  }

  @GetMapping("/api/puzzles/themes")
  public Map<String, Integer> themes() {
    return puzzles.themes();
  }

  @GetMapping("/api/puzzles/{id}")
  public PuzzleView get(@PathVariable String id) {
    return puzzles.get(id);
  }

  public record AttemptRequest(boolean solved) {}

  @Operation(summary = "Report whether you solved a puzzle; returns your new puzzle rating")
  @PostMapping("/api/puzzles/{id}/attempt")
  public AttemptResult attempt(
      @AuthenticationPrincipal AuthUser me, @PathVariable String id, @RequestBody AttemptRequest body) {
    return puzzles.attempt(me.id(), id, body.solved());
  }

  @Operation(summary = "Puzzles for one Puzzle Rush run, easy to hard")
  @GetMapping("/api/puzzles/rush")
  public List<PuzzleView> rush() {
    return puzzles.rush();
  }

  public record RushScore(@Min(0) @Max(200) int score, @Pattern(regexp = "3min|survival") String mode) {}

  @PostMapping("/api/puzzles/rush/score")
  public Map<String, Integer> rushScore(@AuthenticationPrincipal AuthUser me, @RequestBody RushScore body) {
    return Map.of("best", puzzles.saveRush(me.id(), body.score(), body.mode()));
  }

  @GetMapping("/api/puzzles/rush/best")
  public Map<String, Integer> rushBest(@AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "3min") String mode) {
    return Map.of("best", puzzles.bestRush(me.id(), mode));
  }
}
