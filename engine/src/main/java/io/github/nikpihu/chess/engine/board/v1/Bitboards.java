package io.github.nikpihu.chess.engine.board.v1;

/**
 * A bitboard is a {@code long}: bit n set means "something is on square n".
 *
 * <p>The operations here are the building blocks used everywhere later (move generation,
 * evaluation). Typical loop over every square in a bitboard:
 *
 * <pre>{@code
 * while (bb != 0) {
 *   int sq = Bitboards.lowestSquare(bb);
 *   // ... use sq ...
 *   bb = Bitboards.withoutLowest(bb);
 * }
 * }</pre>
 */
public final class Bitboards {

  public static final long EMPTY = 0L;
  public static final long FULL = ~0L;

  public static final long FILE_A = 0x0101010101010101L;
  public static final long FILE_H = FILE_A << 7;
  public static final long RANK_1 = 0xFFL;
  public static final long RANK_8 = RANK_1 << 56;

  /** FILES[f] has every square on file f set (0 = a). */
  public static final long[] FILES = new long[8];

  /** RANKS[r] has every square on rank r set (0 = rank 1). */
  public static final long[] RANKS = new long[8];

  static {
    for (int i = 0; i < 8; i++) {
      FILES[i] = FILE_A << i;
      RANKS[i] = RANK_1 << (8 * i);
    }
  }

  private Bitboards() {}

  /** A bitboard with only {@code square} set. */
  public static long of(int square) {
    return 1L << square;
  }

  public static boolean contains(long bb, int square) {
    return (bb >>> square & 1L) != 0;
  }

  /** Number of set squares (population count). Compiles to a single CPU instruction. */
  public static int count(long bb) {
    return Long.bitCount(bb);
  }

  /** Index of the lowest set square. Only meaningful when {@code bb != 0}. */
  public static int lowestSquare(long bb) {
    return Long.numberOfTrailingZeros(bb);
  }

  /** Clears the lowest set bit: {@code x & (x - 1)}. */
  public static long withoutLowest(long bb) {
    return bb & (bb - 1);
  }
}
