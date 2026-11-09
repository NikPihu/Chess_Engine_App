package com.gambyte.game.domain;

/** Where a game stands. Everything except {@link #STARTED} is final. */
public enum GameStatus {
  STARTED,
  /** Cancelled before both players moved; not rated, no winner. */
  ABORTED,
  MATE,
  RESIGN,
  STALEMATE,
  /** A player ran out of time (the other wins, or draw if they cannot possibly mate). */
  TIMEOUT,
  /** Draw by agreement, repetition, 50-move rule or insufficient material. */
  DRAW,
  /** A player left the game and did not come back in time. */
  ABANDONED,
  /** Ended by a moderator for fair-play violation. */
  CHEAT;

  public boolean isOver() {
    return this != STARTED;
  }
}
