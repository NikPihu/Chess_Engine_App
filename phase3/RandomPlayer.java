package phase3;

import java.util.Random;
import phase1.Move;
import phase2.MoveGenerator;
import phase2.MoveList;
import phase2.Position;

/**
 * Engine "v0": plays a uniformly random legal move. Useless as a player, perfect as a first
 * opponent and as the bottom anchor of our rating ladder (Phase 5 and the LLM Arena).
 */
public final class RandomPlayer implements Searcher {

  private final Random random;

  public RandomPlayer() {
    this(new Random());
  }

  public RandomPlayer(Random random) {
    this.random = random;
  }

  @Override
  public String name() {
    return "random";
  }

  @Override
  public int bestMove(Position position, SearchLimits limits, SearchContext context) {
    MoveList moves = new MoveList();
    MoveGenerator.generate(position, moves);
    if (moves.size() == 0) {
      return Move.NULL;
    }
    context.info("info string random move out of " + moves.size());
    return moves.get(random.nextInt(moves.size()));
  }
}
