package com.gambyte.puzzle;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * A tactics puzzle: a position where the side to move has one winning line. Puzzles have their own
 * Glicko-2 rating, updated every time someone tries them, so their difficulty settles by itself.
 */
@Entity
@Table(name = "puzzles")
public class Puzzle {

  @Id private String id;
  private String fen;
  private String setupMove;
  private String solution;
  private double rating;
  private double deviation = 200;
  private double volatility = 0.06;
  private int plays;
  private String themes = "";
  private String source;
  private Instant createdAt = Instant.now();

  protected Puzzle() {}

  public Puzzle(String id, String fen, String setupMove, String solution, double rating, String themes, String source) {
    this.id = id;
    this.fen = fen;
    this.setupMove = setupMove;
    this.solution = solution;
    this.rating = rating;
    this.themes = themes == null ? "" : themes;
    this.source = source;
  }

  public List<String> solutionMoves() {
    return Arrays.asList(solution.split(" "));
  }

  public List<String> themeList() {
    return themes.isBlank() ? List.of() : Arrays.asList(themes.split(" "));
  }

  public void updateRating(double rating, double deviation, double volatility) {
    this.rating = rating;
    this.deviation = deviation;
    this.volatility = volatility;
    this.plays++;
  }

  public String getId() {
    return id;
  }

  public String getFen() {
    return fen;
  }

  public String getSetupMove() {
    return setupMove;
  }

  public double getRating() {
    return rating;
  }

  public double getDeviation() {
    return deviation;
  }

  public double getVolatility() {
    return volatility;
  }

  public int getPlays() {
    return plays;
  }

  public String getSource() {
    return source;
  }
}
