package com.gambyte.user.repo;

import com.gambyte.user.domain.Rating;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Database access for {@link Rating}. */
public interface RatingRepository extends JpaRepository<Rating, Rating.Key> {

  @Query("select r from Rating r where r.id.userId = ?1")
  List<Rating> findByUserId(long userId);

  /**
   * Leaderboard: established (non-provisional) players of one pool, best first. Uses the
   * (perf, rating desc) index.
   */
  @Query(
      "select r from Rating r where r.id.perf = ?1 and r.deviation <= 110 and r.games >= 5"
          + " order by r.rating desc")
  List<Rating> leaderboard(String perf, Pageable page);
}
