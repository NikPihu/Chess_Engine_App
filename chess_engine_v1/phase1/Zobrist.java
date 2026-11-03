package phase1;

/**
 * Zobrist hashing: a 64-bit fingerprint of a position.
 *
 * <p>Every "feature" of a position gets a fixed random 64-bit number: each (piece, square) pair,
 * each castling-rights combination, each en-passant file, and "black to move". The hash of a
 * position is the XOR of the numbers of every feature it has.
 *
 * <p>Because {@code x ^ k ^ k == x}, a change is undone by XOR-ing the same key again. So making a
 * move only needs a few XORs (remove piece from old square, add on new square, flip side) instead
 * of rehashing the whole board. {@link Board} does these updates itself; {@link #compute(Board)}
 * hashes from scratch and is used by tests to prove the incremental key is always right.
 *
 * <p>The random numbers come from a fixed seed, so the same position always has the same hash on
 * every run and every machine (needed later for opening books and saved test results).
 */
public final class Zobrist {

  private static final long SEED = 0x5EB1A6E2021C0DE5L;

  /** PIECE_SQUARE[piece][square]. Rows for unused piece codes stay 0. */
  static final long[][] PIECE_SQUARE = new long[Piece.CODE_COUNT][Square.COUNT];

  /** CASTLING[rights] for all 16 combinations; CASTLING[0] is 0 so "no rights" changes nothing. */
  static final long[] CASTLING = new long[16];

  /** EN_PASSANT_FILE[file], used only when an en-passant square is set. */
  static final long[] EN_PASSANT_FILE = new long[8];

  /** XOR-ed in when it is black's turn. */
  static final long BLACK_TO_MOVE;

  static {
    SplitMix64 random = new SplitMix64(SEED);
    for (int piece : Piece.ALL) {
      for (int square = 0; square < Square.COUNT; square++) {
        PIECE_SQUARE[piece][square] = random.next();
      }
    }
    for (int rights = 1; rights < 16; rights++) {
      CASTLING[rights] = random.next();
    }
    for (int file = 0; file < 8; file++) {
      EN_PASSANT_FILE[file] = random.next();
    }
    BLACK_TO_MOVE = random.next();
  }

  private Zobrist() {}

  /** Hashes a board from scratch. Slow (visits every piece); used for checking, not in search. */
  public static long compute(Board board) {
    long key = 0;
    for (int square = 0; square < Square.COUNT; square++) {
      int piece = board.pieceAt(square);
      if (piece != Piece.NONE) {
        key ^= PIECE_SQUARE[piece][square];
      }
    }
    key ^= CASTLING[board.castlingRights()];
    if (board.enPassantSquare() != Square.NONE) {
      key ^= EN_PASSANT_FILE[Square.file(board.enPassantSquare())];
    }
    if (board.sideToMove() == Colour.BLACK) {
      key ^= BLACK_TO_MOVE;
    }
    return key;
  }

  /**
   * SplitMix64, a tiny, well-known random generator. Written out here instead of using {@code
   * java.util.Random} so the sequence is guaranteed never to change between Java versions.
   */
  private static final class SplitMix64 {
    private long state;

    SplitMix64(long seed) {
      this.state = seed;
    }

    long next() {
      long z = (state += 0x9E3779B97F4A7C15L);
      z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
      z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
      return z ^ (z >>> 31);
    }
  }
}
