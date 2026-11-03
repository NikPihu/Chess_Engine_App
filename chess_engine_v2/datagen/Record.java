package datagen;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import nnue.Features;
import phase1.Bitboards;
import phase1.Board;
import phase1.Colour;

/**
 * One training example, written as a fixed 72-byte record so the Python trainer can load millions
 * of them instantly with {@code numpy.memmap} (no FEN parsing in Python).
 *
 * <pre>
 *   offset  size  field
 *        0    64  32 x uint16  active features from WHITE's perspective (unused = 768)
 *       64     2  int16        search score, centipawns, from WHITE's point of view
 *       66     1  uint8        side to move: 0 = white, 1 = black
 *       67     1  uint8        game result from white's view: 0 = loss, 1 = draw, 2 = win
 *       68     1  uint8        sample weight (1 normal, 4 = Lichess blunder position)
 *       69     3  reserved
 * </pre>
 *
 * Black's perspective features are not stored: the trainer derives them with the same rule as
 * {@link Features#flip}. (Must match {@code train/data.py}.)
 */
public final class Record {

  public static final int SIZE = 72;
  public static final int MAX_FEATURES = 32;
  public static final int PAD = Features.COUNT;

  private Record() {}

  /**
   * @param whiteScore centipawns from white's point of view
   * @param result 0 black won, 1 draw, 2 white won
   */
  public static void write(ByteBuffer out, Board board, int whiteScore, int result, int weight) {
    out.order(ByteOrder.LITTLE_ENDIAN);
    int start = out.position();
    long occupied = board.occupied();
    int n = 0;
    while (occupied != 0 && n < MAX_FEATURES) {
      int sq = Bitboards.lowestSquare(occupied);
      occupied = Bitboards.withoutLowest(occupied);
      out.putShort((short) Features.index(Colour.WHITE, board.pieceAt(sq), sq));
      n++;
    }
    for (; n < MAX_FEATURES; n++) {
      out.putShort((short) PAD);
    }
    out.putShort((short) Math.max(-32000, Math.min(32000, whiteScore)));
    out.put((byte) board.sideToMove());
    out.put((byte) result);
    out.put((byte) weight);
    out.put((byte) 0).put((byte) 0).put((byte) 0);
    if (out.position() - start != SIZE) {
      throw new IllegalStateException("record size mismatch");
    }
  }
}
