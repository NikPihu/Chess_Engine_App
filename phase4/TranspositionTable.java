package phase4;

/**
 * Transposition table (TT): a big hash table remembering positions we have already searched.
 *
 * <p>The same position is reached by different move orders (1.e4 e5 2.Nf3 = 1.Nf3 e5 2.e4) — a
 * "transposition". If we searched it before to at least the depth we need now, we can reuse the
 * result instead of searching again. And even when we can't, the best move we found last time is
 * the best guess to try first (great for move ordering).
 *
 * <p><b>Layout.</b> Two parallel {@code long[]} arrays (no objects → no garbage): the full Zobrist
 * key, and one packed entry:
 *
 * <pre>
 *   bits  0-15  best move (16 bits)
 *   bits 16-31  score (signed 16 bits)
 *   bits 32-39  depth
 *   bits 40-41  bound: EXACT, LOWER (score is at least), UPPER (score is at most)
 *   bits 42-49  age (which search wrote it)
 * </pre>
 *
 * The slot is {@code key & (size - 1)} (size is a power of two). <b>Replacement</b> (where Lague's
 * always-replace table lost useful deep results): an old entry is kept only if it is from the
 * current search, about a different position, and deeper than the new one.
 *
 * <p><b>Mate scores</b> are stored relative to the node, not the root: "mate in 3 from here" stays
 * correct wherever the position is reached again. That fixes the bug Lague suspected in V1.
 */
public final class TranspositionTable {

  public static final int EXACT = 0;
  public static final int LOWER = 1;
  public static final int UPPER = 2;

  /** Returned by {@link #probe} when nothing is stored. */
  public static final long MISS = -1L;

  private long[] keys;
  private long[] entries;
  private int mask;
  private int age;

  public TranspositionTable(int megabytes) {
    resize(megabytes);
  }

  public void resize(int megabytes) {
    long bytes = Math.max(1, megabytes) * 1024L * 1024L;
    int count = Integer.highestOneBit((int) Math.min(bytes / 16, 1 << 30));
    keys = new long[count];
    entries = new long[count];
    mask = count - 1;
  }

  public void clear() {
    java.util.Arrays.fill(keys, 0);
    java.util.Arrays.fill(entries, 0);
    age = 0;
  }

  /** Call once per new search so entries from older searches can be replaced first. */
  public void newSearch() {
    age = (age + 1) & 0xFF;
  }

  /**
   * @return the packed entry for {@code key}, or {@link #MISS}
   */
  public long probe(long key) {
    int i = (int) key & mask;
    return keys[i] == key && entries[i] != 0 ? entries[i] : MISS;
  }

  public void store(long key, int move, int score, int depth, int bound, int ply) {
    int i = (int) key & mask;
    long old = entries[i];
    if (old != 0 && keys[i] != key && age(old) == age && depth(old) > depth) {
      return; // keep the deeper result from this search
    }
    if (keys[i] == key && move == 0) {
      move = move(old); // don't lose a known best move
    }
    entries[i] =
        (move & 0xFFFFL)
            | ((scoreToTt(score, ply) & 0xFFFFL) << 16)
            | ((long) Math.max(0, depth) & 0xFF) << 32
            | ((long) bound & 0x3) << 40
            | ((long) age & 0xFF) << 42
            | 1L << 62; // marks the entry as used, so it is never 0
    keys[i] = key;
  }

  /** Permille of slots used by the current search (UCI "hashfull"), sampled from 1000 slots. */
  public int hashfull() {
    int used = 0;
    int samples = Math.min(1000, entries.length);
    for (int i = 0; i < samples; i++) {
      if (entries[i] != 0 && age(entries[i]) == age) {
        used++;
      }
    }
    return used * 1000 / samples;
  }

  public static int move(long entry) {
    return (int) (entry & 0xFFFF);
  }

  /** Score adjusted back to "relative to the root" for a node at {@code ply}. */
  public static int score(long entry, int ply) {
    int s = (short) ((entry >>> 16) & 0xFFFF);
    if (s >= Search.MATE_THRESHOLD) {
      return s - ply;
    }
    if (s <= -Search.MATE_THRESHOLD) {
      return s + ply;
    }
    return s;
  }

  public static int depth(long entry) {
    return (int) ((entry >>> 32) & 0xFF);
  }

  public static int bound(long entry) {
    return (int) ((entry >>> 40) & 0x3);
  }

  private static int age(long entry) {
    return (int) ((entry >>> 42) & 0xFF);
  }

  private static int scoreToTt(int score, int ply) {
    if (score >= Search.MATE_THRESHOLD) {
      return score + ply;
    }
    if (score <= -Search.MATE_THRESHOLD) {
      return score - ply;
    }
    return score;
  }
}
