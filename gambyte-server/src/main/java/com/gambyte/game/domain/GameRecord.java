package com.gambyte.game.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * A game as stored in Postgres. For a running game the authoritative state is {@code LiveGame}
 * in memory; this row is created at the start and completed at the end.
 */
@Entity
@Table(name = "games")
public class GameRecord {

  @Id private String id;
  private long whiteId;
  private long blackId;
  private Integer whiteRating;
  private Integer blackRating;
  private Integer whiteRatingDiff;
  private Integer blackRatingDiff;
  private Integer engineLevel;
  private String perf;
  private int clockInitial;
  private int clockIncrement;
  private boolean rated;
  private String source;

  @Enumerated(EnumType.STRING)
  private GameStatus status;

  private String winner;
  private String termination;
  private String startFen;
  private String moves = "";
  private String moveTimes = "";
  private String finalFen;
  private int ply;
  private Long tournamentId;
  private String opening;
  private Instant createdAt = Instant.now();
  private Instant endedAt;

  protected GameRecord() {}

  public GameRecord(
      String id,
      long whiteId,
      long blackId,
      String perf,
      int clockInitial,
      int clockIncrement,
      boolean rated,
      String source,
      String startFen) {
    this.id = id;
    this.whiteId = whiteId;
    this.blackId = blackId;
    this.perf = perf;
    this.clockInitial = clockInitial;
    this.clockIncrement = clockIncrement;
    this.rated = rated;
    this.source = source;
    this.startFen = startFen;
    this.status = GameStatus.STARTED;
  }

  public List<String> moveList() {
    return moves.isBlank() ? List.of() : Arrays.asList(moves.split(" "));
  }

  public List<Integer> moveTimeList() {
    return moveTimes.isBlank()
        ? List.of()
        : Arrays.stream(moveTimes.split(",")).map(Integer::parseInt).toList();
  }

  /** Called once when the game ends. */
  public void finish(
      GameStatus status,
      String winner,
      String termination,
      List<String> moves,
      List<Integer> moveTimes,
      String finalFen) {
    this.status = status;
    this.winner = winner;
    this.termination = termination;
    this.moves = String.join(" ", moves);
    this.moveTimes = String.join(",", moveTimes.stream().map(String::valueOf).toList());
    this.finalFen = finalFen;
    this.ply = moves.size();
    this.endedAt = Instant.now();
  }

  // Getters and the few setters other code needs.

  public String getId() {
    return id;
  }

  public long getWhiteId() {
    return whiteId;
  }

  public long getBlackId() {
    return blackId;
  }

  public Integer getWhiteRating() {
    return whiteRating;
  }

  public Integer getBlackRating() {
    return blackRating;
  }

  public void setRatings(Integer white, Integer black) {
    this.whiteRating = white;
    this.blackRating = black;
  }

  public Integer getWhiteRatingDiff() {
    return whiteRatingDiff;
  }

  public Integer getBlackRatingDiff() {
    return blackRatingDiff;
  }

  public void setRatingDiffs(Integer white, Integer black) {
    this.whiteRatingDiff = white;
    this.blackRatingDiff = black;
  }

  public Integer getEngineLevel() {
    return engineLevel;
  }

  public void setEngineLevel(Integer engineLevel) {
    this.engineLevel = engineLevel;
  }

  public String getPerf() {
    return perf;
  }

  public int getClockInitial() {
    return clockInitial;
  }

  public int getClockIncrement() {
    return clockIncrement;
  }

  public boolean isRated() {
    return rated;
  }

  public String getSource() {
    return source;
  }

  public GameStatus getStatus() {
    return status;
  }

  public String getWinner() {
    return winner;
  }

  public String getTermination() {
    return termination;
  }

  public String getStartFen() {
    return startFen;
  }

  public String getFinalFen() {
    return finalFen;
  }

  public int getPly() {
    return ply;
  }

  public Long getTournamentId() {
    return tournamentId;
  }

  public void setTournamentId(Long tournamentId) {
    this.tournamentId = tournamentId;
  }

  public String getOpening() {
    return opening;
  }

  public void setOpening(String opening) {
    this.opening = opening;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }
}
