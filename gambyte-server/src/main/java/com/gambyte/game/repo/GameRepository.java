package com.gambyte.game.repo;

import com.gambyte.game.domain.GameRecord;
import com.gambyte.game.domain.GameStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Database access for {@link GameRecord}. */
public interface GameRepository extends JpaRepository<GameRecord, String> {

  List<GameRecord> findByStatus(GameStatus status);

  /**
   * A player's finished games, newest first, older than {@code before}. "Keyset pagination": the
   * next page starts where the last one ended, which stays fast however deep you scroll (unlike
   * OFFSET, which re-reads every skipped row).
   */
  @Query(
      "select g from GameRecord g where (g.whiteId = ?1 or g.blackId = ?1) and g.createdAt < ?2"
          + " and g.status <> com.gambyte.game.domain.GameStatus.STARTED"
          + " order by g.createdAt desc")
  List<GameRecord> archive(long userId, Instant before, Pageable page);

  @Query(
      "select count(g) from GameRecord g where (g.whiteId = ?1 or g.blackId = ?1)"
          + " and g.status <> com.gambyte.game.domain.GameStatus.STARTED")
  long countFinished(long userId);

  List<GameRecord> findByTournamentId(long tournamentId);
}
