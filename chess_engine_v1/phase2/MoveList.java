package phase2;

/**
 * A reusable list of moves backed by a plain {@code int[]}.
 *
 * <p>No chess position has more than 218 legal moves, so 256 slots always suffice. The search keeps
 * one MoveList per depth and reuses it, so generating moves never allocates memory (no garbage
 * collector pauses in the middle of thinking).
 */
public final class MoveList {

  public static final int CAPACITY = 256;

  private final int[] moves = new int[CAPACITY];
  private int size;

  public void clear() {
    size = 0;
  }

  public void add(int move) {
    moves[size++] = move;
  }

  public int size() {
    return size;
  }

  public int get(int index) {
    return moves[index];
  }

  public void set(int index, int move) {
    moves[index] = move;
  }

  /** Swaps two entries (used by move ordering). */
  public void swap(int i, int j) {
    int t = moves[i];
    moves[i] = moves[j];
    moves[j] = t;
  }

  public boolean contains(int move) {
    for (int i = 0; i < size; i++) {
      if (moves[i] == move) {
        return true;
      }
    }
    return false;
  }
}
