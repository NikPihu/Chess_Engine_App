package io.github.nikpihu.chess.engine.display.v1;

import io.github.nikpihu.chess.engine.board.v1.Bitboards;
import io.github.nikpihu.chess.engine.board.v1.Square;

/**
 * Draws a single bitboard as an 8x8 grid ('1' = bit set, '.' = not set), rank 8 at the top. The
 * best way to see what a {@code long} bitboard really contains.
 */
public final class BitboardPrinter {

  private BitboardPrinter() {}

  public static String print(long bb) {
    StringBuilder sb = new StringBuilder();
    for (int rank = 7; rank >= 0; rank--) {
      sb.append(' ').append(rank + 1).append("  ");
      for (int file = 0; file < 8; file++) {
        sb.append(Bitboards.contains(bb, Square.of(file, rank)) ? '1' : '.');
        sb.append(file < 7 ? " " : "\n");
      }
    }
    sb.append("\n    a b c d e f g h\n\n");
    sb.append("hex:   ").append(String.format("0x%016X", bb)).append('\n');
    sb.append("count: ").append(Bitboards.count(bb)).append('\n');
    return sb.toString();
  }
}
