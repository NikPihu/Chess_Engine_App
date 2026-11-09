package com.gambyte.learn;

import com.gambyte.auth.AuthUser;
import com.gambyte.learn.LessonService.Lesson;
import com.gambyte.learn.LessonService.LessonSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Interactive lessons and the user's progress through them. */
@RestController
@Tag(name = "Learn", description = "Lessons")
public class LessonController {

  private final LessonService lessons;

  public LessonController(LessonService lessons) {
    this.lessons = lessons;
  }

  @GetMapping("/api/lessons")
  public List<LessonSummary> list(@AuthenticationPrincipal AuthUser me) {
    return lessons.list(me == null ? null : me.id());
  }

  @GetMapping("/api/lessons/{slug}")
  public Lesson get(@PathVariable String slug) {
    return lessons.get(slug);
  }

  public record Progress(int completedSteps) {}

  @PostMapping("/api/lessons/{slug}/progress")
  public ResponseEntity<Void> progress(
      @AuthenticationPrincipal AuthUser me, @PathVariable String slug, @RequestBody Progress body) {
    lessons.saveProgress(me.id(), slug, body.completedSteps());
    return ResponseEntity.noContent().build();
  }
}
