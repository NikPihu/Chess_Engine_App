package phase6;

/**
 * History heuristic: a score for every (side, from-square, to-square) quiet move, increased by
 * depth² whenever that move causes a cutoff anywhere in the tree. Moves that are often good get
 * tried earlier everywhere, not just at one ply like killers.
 *
 * <p>Scores are halved at the start of each search so old knowledge fades but isn't lost, and
 * capped so they never outrank captures and killers in the move ordering.
 */
public final class History {

  public static final int MAX = 500_000;

  private final int[][][] table = new int[2][64][64];

  public void age() {
    for (int[][] side : table) {
      for (int[] from : side) {
        for (int to = 0; to < 64; to++) {
          from[to] /= 2;
        }
      }
    }
  }

  public void clear() {
    for (int[][] side : table) {
      for (int[] from : side) {
        java.util.Arrays.fill(from, 0);
      }
    }
  }

  public void reward(int side, int from, int to, int depth) {
    table[side][from][to] = Math.min(MAX, table[side][from][to] + depth * depth);
  }

  public int score(int side, int from, int to) {
    return table[side][from][to];
  }
}
