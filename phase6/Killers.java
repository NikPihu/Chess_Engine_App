package phase6;

import phase4.Search;

/**
 * Killer moves: quiet moves that caused a beta cutoff at the same depth (ply) in a sibling branch.
 * Positions at the same ply are often similar, so a move that "killed" one branch is worth trying
 * early in the next. We keep the two most recent killers per ply.
 */
public final class Killers {

  private final int[][] killers = new int[Search.MAX_PLY + 1][2];

  public void clear() {
    for (int[] k : killers) {
      k[0] = 0;
      k[1] = 0;
    }
  }

  public void add(int ply, int move) {
    if (killers[ply][0] != move) {
      killers[ply][1] = killers[ply][0];
      killers[ply][0] = move;
    }
  }

  public int first(int ply) {
    return killers[ply][0];
  }

  public int second(int ply) {
    return killers[ply][1];
  }
}
