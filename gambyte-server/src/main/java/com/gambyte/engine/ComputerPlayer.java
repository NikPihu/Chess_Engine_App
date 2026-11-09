package com.gambyte.engine;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import phase2.MoveGenerator;
import phase2.MoveList;
import phase2.Position;
import phase2.UciMoveParser;

/** Chooses moves for the Gambyte Engine bot at a given {@link EngineLevel}. */
@Service
public class ComputerPlayer {

  private final EnginePool pool;

  public ComputerPlayer(EnginePool pool) {
    this.pool = pool;
  }

  /**
   * The bot's move in UCI notation, or null if it has no legal move.
   *
   * @param clockMs remaining time of white and black and their increments (used at level 8)
   */
  public String chooseMove(
      String startFen, List<String> moves, EngineLevel level, long[] clockMs) {
    Position position = Position.fromFen(startFen);
    for (String m : moves) position.makeMove(UciMoveParser.parse(position, m));
    MoveList legal = new MoveList();
    MoveGenerator.generate(position, legal);
    if (legal.size() == 0) return null;

    ThreadLocalRandom random = ThreadLocalRandom.current();
    if (level.randomMoveChance > 0 && random.nextDouble() < level.randomMoveChance) {
      return phase1.Move.toUci(legal.get(random.nextInt(legal.size())));
    }
    EngineLimits limits;
    if (level.fullStrength()) {
      limits = clockMs == null
          ? EngineLimits.moveTime(700) // untimed game or first moves: under a second is plenty
          : EngineLimits.clock(clockMs[0], clockMs[1], clockMs[2], clockMs[3]);
    } else {
      limits = EngineLimits.nodes(level.nodes);
    }
    String best = pool.search(startFen, moves, limits).bestMove();
    return "(none)".equals(best) ? phase1.Move.toUci(legal.get(0)) : best;
  }
}
