package phase2;

import phase1.Bitboards;
import phase1.Square;

/**
 * Magic bitboards: rook and bishop attacks in O(1) with a multiply and a table lookup.
 *
 * <p><b>The idea.</b> A rook on d4 only cares about pieces on its rays, and not even the last
 * square of each ray (a piece there changes nothing). Those "relevant" squares are its {@code
 * mask}: at most 12 squares for a rook, 9 for a bishop. Every arrangement of blockers on the mask
 * gives one attack set. We want a table indexed by the blocker arrangement, but the arrangement is
 * scattered bits inside a 64-bit number. The trick: multiply by a special "magic" number, which
 * gathers those scattered bits into the top bits of the product, then shift them down:
 *
 * <pre>
 *   index   = ((occupied & mask[sq]) * magic[sq]) >>> shift[sq]
 *   attacks = table[sq][index]
 * </pre>
 *
 * A magic is valid when no two blocker arrangements with <i>different</i> attacks land on the same
 * index (a hash function without harmful collisions). Nobody derives them by hand: we try random
 * numbers until one works. Lague's V2 pasted pre-found magics; here we find our own at start-up (a
 * fixed seed makes it repeatable, and it takes a few milliseconds).
 */
public final class Magics {

  private static final long[] ROOK_MASK = new long[64];
  private static final long[] ROOK_MAGIC = new long[64];
  private static final int[] ROOK_SHIFT = new int[64];
  private static final long[][] ROOK_TABLE = new long[64][];

  private static final long[] BISHOP_MASK = new long[64];
  private static final long[] BISHOP_MAGIC = new long[64];
  private static final int[] BISHOP_SHIFT = new int[64];
  private static final long[][] BISHOP_TABLE = new long[64][];

  private static final long SEED = 0x2021_C0DE_C4E5_5EEDL;

  static {
    long[] state = {SEED};
    for (int sq = 0; sq < 64; sq++) {
      ROOK_MASK[sq] = rookMask(sq);
      ROOK_SHIFT[sq] = 64 - Long.bitCount(ROOK_MASK[sq]);
      ROOK_TABLE[sq] = new long[1 << Long.bitCount(ROOK_MASK[sq])];
      ROOK_MAGIC[sq] = findMagic(sq, ROOK_MASK[sq], ROOK_SHIFT[sq], ROOK_TABLE[sq], true, state);

      BISHOP_MASK[sq] = bishopMask(sq);
      BISHOP_SHIFT[sq] = 64 - Long.bitCount(BISHOP_MASK[sq]);
      BISHOP_TABLE[sq] = new long[1 << Long.bitCount(BISHOP_MASK[sq])];
      BISHOP_MAGIC[sq] =
          findMagic(sq, BISHOP_MASK[sq], BISHOP_SHIFT[sq], BISHOP_TABLE[sq], false, state);
    }
  }

  private Magics() {}

  public static long rookAttacks(int sq, long occupied) {
    int index = (int) (((occupied & ROOK_MASK[sq]) * ROOK_MAGIC[sq]) >>> ROOK_SHIFT[sq]);
    return ROOK_TABLE[sq][index];
  }

  public static long bishopAttacks(int sq, long occupied) {
    int index = (int) (((occupied & BISHOP_MASK[sq]) * BISHOP_MAGIC[sq]) >>> BISHOP_SHIFT[sq]);
    return BISHOP_TABLE[sq][index];
  }

  public static long queenAttacks(int sq, long occupied) {
    return rookAttacks(sq, occupied) | bishopAttacks(sq, occupied);
  }

  /** Rook rays on an empty board, minus the board edges at the end of each ray. */
  private static long rookMask(int sq) {
    long edges =
        ((Bitboards.RANK_1 | Bitboards.RANK_8) & ~Bitboards.RANKS[Square.rank(sq)])
            | ((Bitboards.FILE_A | Bitboards.FILE_H) & ~Bitboards.FILES[Square.file(sq)]);
    return RayAttacks.rook(sq, 0) & ~edges;
  }

  /** Bishop rays on an empty board, minus every edge square (a bishop ray always ends there). */
  private static long bishopMask(int sq) {
    long edges = Bitboards.RANK_1 | Bitboards.RANK_8 | Bitboards.FILE_A | Bitboards.FILE_H;
    return RayAttacks.bishop(sq, 0) & ~edges;
  }

  /** Tries random candidates until one maps every blocker subset without a harmful collision. */
  private static long findMagic(
      int sq, long mask, int shift, long[] table, boolean rook, long[] state) {
    int subsetCount = 1 << Long.bitCount(mask);
    long[] blockers = new long[subsetCount];
    long[] attacks = new long[subsetCount];

    // Enumerate all subsets of the mask (the "carry-rippler" trick).
    long subset = 0;
    int n = 0;
    do {
      blockers[n] = subset;
      attacks[n] = rook ? RayAttacks.rook(sq, subset) : RayAttacks.bishop(sq, subset);
      n++;
      subset = (subset - mask) & mask;
    } while (subset != 0);

    while (true) {
      // AND of three randoms has few set bits: sparse candidates work much more often.
      long magic = next(state) & next(state) & next(state);
      if (Long.bitCount((mask * magic) & 0xFF00_0000_0000_0000L) < 6) {
        continue; // quick reject: cannot spread the mask bits well
      }
      java.util.Arrays.fill(table, 0L); // 0 = empty slot (real attack sets are never empty)
      boolean ok = true;
      for (int i = 0; i < subsetCount && ok; i++) {
        int index = (int) ((blockers[i] * magic) >>> shift);
        if (table[index] == 0) {
          table[index] = attacks[i];
        } else if (table[index] != attacks[i]) {
          ok = false; // two different attack sets want the same slot
        }
      }
      if (ok) {
        return magic;
      }
    }
  }

  /** SplitMix64 step (same generator as phase1.Zobrist, independent state). */
  private static long next(long[] state) {
    long z = (state[0] += 0x9E3779B97F4A7C15L);
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
  }
}
