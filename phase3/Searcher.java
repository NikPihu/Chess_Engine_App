package phase3;

import phase2.Position;

/**
 * Anything that can choose a move: the random player now, the real searches of Phase 4 and 6 later.
 * The UCI program and the board viewer only talk to this interface, so every engine version can be
 * plugged in and compared against the others.
 */
public interface Searcher {

  /** Short name shown in the GUI and used by {@code setoption name Engine value <name>}. */
  String name();

  /**
   * Chooses a move for the side to move. Must return a legal move (or {@code Move.NULL} if there
   * are none) and must leave {@code position} exactly as it was.
   */
  int bestMove(Position position, SearchLimits limits, SearchContext context);

  /** Forget everything learned during the previous game (e.g. clear the transposition table). */
  default void newGame() {}

  /** Size of the transposition table in MB, for engines that have one. */
  default void setHashSizeMb(int mb) {}
}
