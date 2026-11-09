package com.gambyte.learn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gambyte.common.ApiException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import phase2.Position;
import phase2.UciMoveParser;

/**
 * Lessons are content, not code: one JSON file per lesson in {@code resources/lessons/}, made of
 * text steps and exercise steps. To add a lesson, add a file - no code change needed.
 *
 * <p>Every exercise position and accepted answer is checked with our rules engine when the server
 * starts. A typo in a lesson therefore stops the server with a clear message instead of showing a
 * student an impossible exercise.
 */
@Service
public class LessonService {

  /** One step: text to read, or a position where the student must find a move. */
  public record Step(String type, String text, String fen, String prompt, List<String> accept, String hint, String success) {}

  public record Lesson(String slug, int order, String category, String level, String title, String summary, List<Step> steps) {}

  public record LessonSummary(String slug, int order, String category, String level, String title, String summary, int steps, int completedSteps) {}

  private final ObjectMapper json;
  private final JdbcTemplate jdbc;
  private final Map<String, Lesson> lessons = new LinkedHashMap<>();

  public LessonService(ObjectMapper json, JdbcTemplate jdbc) {
    this.json = json;
    this.jdbc = jdbc;
  }

  @PostConstruct
  void load() throws IOException {
    List<Lesson> loaded = new ArrayList<>();
    for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath:lessons/*.json")) {
      try (InputStream in = r.getInputStream()) {
        Lesson lesson = json.readValue(in, Lesson.class);
        validate(lesson);
        loaded.add(lesson);
      }
    }
    loaded.sort(Comparator.comparingInt(Lesson::order));
    loaded.forEach(l -> lessons.put(l.slug(), l));
  }

  static void validate(Lesson lesson) {
    for (Step s : lesson.steps()) {
      if (!"exercise".equals(s.type())) continue;
      Position p;
      try {
        p = Position.fromFen(s.fen());
      } catch (RuntimeException e) {
        throw new IllegalStateException("Lesson " + lesson.slug() + ": bad FEN " + s.fen(), e);
      }
      for (String move : s.accept()) {
        if (UciMoveParser.parse(p, move) == phase1.Move.NULL) {
          throw new IllegalStateException("Lesson " + lesson.slug() + ": illegal answer " + move + " in " + s.fen());
        }
      }
    }
  }

  public List<LessonSummary> list(Long userId) {
    Map<String, Integer> progress = userId == null ? Map.of() : progress(userId);
    return lessons.values().stream()
        .map(l -> new LessonSummary(l.slug(), l.order(), l.category(), l.level(), l.title(), l.summary(),
            l.steps().size(), progress.getOrDefault(l.slug(), 0)))
        .toList();
  }

  public Lesson get(String slug) {
    Lesson l = lessons.get(slug);
    if (l == null) throw ApiException.notFound("lesson");
    return l;
  }

  /** Remembers how far the user got (never goes backwards). */
  public void saveProgress(long userId, String slug, int completedSteps) {
    Lesson l = get(slug);
    int steps = Math.max(0, Math.min(completedSteps, l.steps().size()));
    jdbc.update("""
        INSERT INTO lesson_progress (user_id, slug, completed_steps) VALUES (?, ?, ?)
        ON CONFLICT (user_id, slug) DO UPDATE
          SET completed_steps = GREATEST(lesson_progress.completed_steps, EXCLUDED.completed_steps),
              updated_at = now()""", userId, slug, steps);
  }

  private Map<String, Integer> progress(long userId) {
    Map<String, Integer> out = new LinkedHashMap<>();
    jdbc.query("SELECT slug, completed_steps FROM lesson_progress WHERE user_id = ?",
        rs -> {
          out.put(rs.getString(1), rs.getInt(2));
        }, userId);
    return out;
  }
}
