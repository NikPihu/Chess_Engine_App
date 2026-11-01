package phase3;

/** How a game stands: still going, or finished and why. */
public enum GameResult {
  ONGOING("*", "game in progress"),
  WHITE_WINS("1-0", "white wins by checkmate"),
  BLACK_WINS("0-1", "black wins by checkmate"),
  STALEMATE("1/2-1/2", "draw by stalemate"),
  FIFTY_MOVE_RULE("1/2-1/2", "draw by the 50-move rule"),
  THREEFOLD_REPETITION("1/2-1/2", "draw by threefold repetition"),
  INSUFFICIENT_MATERIAL("1/2-1/2", "draw by insufficient material");

  /** PGN result text: "1-0", "0-1", "1/2-1/2" or "*". */
  public final String pgn;

  public final String description;

  GameResult(String pgn, String description) {
    this.pgn = pgn;
    this.description = description;
  }

  public boolean isOver() {
    return this != ONGOING;
  }
}
