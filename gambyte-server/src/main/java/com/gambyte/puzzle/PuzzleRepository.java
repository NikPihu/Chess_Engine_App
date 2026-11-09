package com.gambyte.puzzle;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Database access for puzzles, attempts and rush runs (native SQL where it is clearer). */
public interface PuzzleRepository extends JpaRepository<Puzzle, String> {

  /**
   * Up to 30 random puzzles in a rating band that this user has not tried, optionally with a
   * theme. The rating index narrows the scan; random() picks among the candidates.
   */
  @Query(
      value =
          """
          SELECT * FROM puzzles p
          WHERE p.rating BETWEEN :low AND :high
            AND (:theme = '' OR (' ' || p.themes || ' ') LIKE ('% ' || :theme || ' %'))
            AND NOT EXISTS (SELECT 1 FROM puzzle_attempts a
                            WHERE a.user_id = :userId AND a.puzzle_id = p.id)
          ORDER BY random() LIMIT 30
          """,
      nativeQuery = true)
  List<Puzzle> candidates(double low, double high, String theme, long userId);

  /** Puzzles across a rating range in order, for Puzzle Rush (easy first). */
  @Query(
      value = "SELECT * FROM (SELECT * FROM puzzles WHERE rating BETWEEN :low AND :high"
          + " ORDER BY random() LIMIT :n) x ORDER BY rating",
      nativeQuery = true)
  List<Puzzle> ladder(double low, double high, int n);

  @Query(value = "SELECT count(*) FROM puzzle_attempts WHERE user_id = :userId AND created_at > :since", nativeQuery = true)
  int attemptsSince(long userId, Instant since);

  @Modifying
  @Query(
      value = "INSERT INTO puzzle_attempts (user_id, puzzle_id, solved) VALUES (:userId, :puzzleId, :solved)"
          + " ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int recordAttempt(long userId, String puzzleId, boolean solved);

  @Query(value = "SELECT count(*) FROM puzzle_attempts WHERE user_id = :userId AND solved", nativeQuery = true)
  int solvedCount(long userId);

  @Modifying
  @Query(value = "INSERT INTO puzzle_rush_runs (user_id, score, mode) VALUES (:userId, :score, :mode)", nativeQuery = true)
  void saveRush(long userId, int score, String mode);

  @Query(value = "SELECT coalesce(max(score), 0) FROM puzzle_rush_runs WHERE user_id = :userId AND mode = :mode", nativeQuery = true)
  int bestRush(long userId, String mode);

  @Query(value = "SELECT themes FROM puzzles", nativeQuery = true)
  List<String> allThemes();
}
