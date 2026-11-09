package com.gambyte.puzzle;

import com.gambyte.common.ApiException;
import com.gambyte.config.GambyteProperties;
import com.gambyte.rating.Glicko2;
import com.gambyte.user.domain.Perf;
import com.gambyte.user.domain.Rating;
import com.gambyte.user.domain.User;
import com.gambyte.user.service.UserService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serving puzzles at the right difficulty and rating both sides afterwards.
 *
 * <p>Solving a puzzle is treated as a game between the player and the puzzle: both have Glicko-2
 * ratings, a solve is a win for the player, a miss a win for the puzzle. Over time good players
 * climb and too-easy puzzles sink, with no manual tuning.
 */
@Service
public class PuzzleService {

  private final PuzzleRepository repo;
  private final UserService users;
  private final GambyteProperties props;

  public PuzzleService(PuzzleRepository repo, UserService users, GambyteProperties props) {
    this.repo = repo;
    this.users = users;
    this.props = props;
  }

  /** What the client gets: the position, the solution (checked in the browser) and metadata. */
  public record PuzzleView(
      String id, String fen, String setupMove, List<String> solution, int rating, int plays, List<String> themes) {
    static PuzzleView of(Puzzle p) {
      return new PuzzleView(p.getId(), p.getFen(), p.getSetupMove(), p.solutionMoves(),
          (int) Math.round(p.getRating()), p.getPlays(), p.themeList());
    }
  }

  public record AttemptResult(int rating, int diff, int solvedTotal) {}

  /** A puzzle near the player's puzzle rating (1500 for visitors), optionally of one theme. */
  @Transactional(readOnly = true)
  public PuzzleView next(Long userId, String theme) {
    double target = userId == null ? 1500 : users.ratingFor(userId, Perf.PUZZLE).getRating();
    String t = theme == null ? "" : theme;
    long uid = userId == null ? -1 : userId;
    // Widen the band until something is found.
    for (int band : new int[] {150, 300, 600, 3000}) {
      List<Puzzle> c = repo.candidates(target - band, target + band, t, uid);
      if (!c.isEmpty()) return PuzzleView.of(c.get(ThreadLocalRandom.current().nextInt(c.size())));
    }
    throw ApiException.notFound("puzzle");
  }

  public PuzzleView get(String id) {
    return PuzzleView.of(repo.findById(id).orElseThrow(() -> ApiException.notFound("puzzle")));
  }

  /** The same puzzle for everyone today (chosen from the date, so no job is needed). */
  @Cacheable(value = "dailyPuzzle", key = "T(java.time.LocalDate).now(T(java.time.ZoneOffset).UTC)")
  public PuzzleView daily() {
    List<Puzzle> ladder = repo.ladder(1500, 2100, 400);
    if (ladder.isEmpty()) ladder = repo.ladder(0, 4000, 400);
    if (ladder.isEmpty()) throw ApiException.notFound("puzzle");
    long day = LocalDate.now(ZoneOffset.UTC).toEpochDay();
    ladder.sort(java.util.Comparator.comparing(Puzzle::getId));
    return PuzzleView.of(ladder.get((int) (day % ladder.size())));
  }

  /**
   * Records a solve or miss and updates both ratings. Each puzzle counts once per player; free
   * accounts get {@code free-puzzles-per-day} rated puzzles a day.
   */
  @Transactional
  public AttemptResult attempt(long userId, String puzzleId, boolean solved) {
    User u = users.get(userId);
    if (!u.isPremium()) {
      int today = repo.attemptsSince(userId, Instant.now().truncatedTo(ChronoUnit.DAYS));
      if (today >= props.limits().freePuzzlesPerDay()) {
        throw ApiException.premiumRequired("Daily puzzle limit reached - go Premium for unlimited puzzles");
      }
    }
    Puzzle p = repo.findById(puzzleId).orElseThrow(() -> ApiException.notFound("puzzle"));
    Rating r = users.ratingFor(userId, Perf.PUZZLE);
    int before = (int) Math.round(r.getRating());
    if (repo.recordAttempt(userId, puzzleId, solved) == 0) {
      return new AttemptResult(before, 0, repo.solvedCount(userId)); // already tried: no change
    }
    Glicko2.Rating player = new Glicko2.Rating(r.getRating(), r.getDeviation(), r.getVolatility());
    Glicko2.Rating puzzle = new Glicko2.Rating(p.getRating(), p.getDeviation(), p.getVolatility());
    Glicko2.Rating newPlayer = Glicko2.update(player, puzzle, solved ? 1 : 0);
    Glicko2.Rating newPuzzle = Glicko2.update(puzzle, player, solved ? 0 : 1);
    r.update(newPlayer.rating(), newPlayer.deviation(), newPlayer.volatility());
    users.saveRating(r);
    p.updateRating(newPuzzle.rating(), Math.max(60, newPuzzle.deviation()), newPuzzle.volatility());
    int after = (int) Math.round(newPlayer.rating());
    return new AttemptResult(after, after - before, repo.solvedCount(userId));
  }

  /** Puzzle Rush: 60 puzzles from easy (~700) to very hard (~2600). */
  public List<PuzzleView> rush() {
    List<PuzzleView> out = new java.util.ArrayList<>();
    int[][] bands = {{0, 1000, 10}, {1000, 1400, 12}, {1400, 1800, 14}, {1800, 2200, 14}, {2200, 4000, 10}};
    for (int[] b : bands) repo.ladder(b[0], b[1], b[2]).forEach(p -> out.add(PuzzleView.of(p)));
    return out;
  }

  @Transactional
  public int saveRush(long userId, int score, String mode) {
    if (score < 0 || score > 200) throw ApiException.badRequest("bad_score", "Invalid score");
    repo.saveRush(userId, score, mode);
    return repo.bestRush(userId, mode);
  }

  public int bestRush(long userId, String mode) {
    return repo.bestRush(userId, mode);
  }

  /** Themes and how many puzzles each has, for the theme picker. */
  @Cacheable("puzzleThemes")
  public Map<String, Integer> themes() {
    Map<String, Integer> counts = new TreeMap<>();
    for (String row : repo.allThemes()) {
      for (String t : row.split(" ")) if (!t.isBlank()) counts.merge(t, 1, Integer::sum);
    }
    return counts;
  }
}
