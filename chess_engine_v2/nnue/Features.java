package nnue;

import phase1.Colour;
import phase1.Piece;

/**
 * The network's inputs: 768 on/off "features", one per (piece type, colour, square).
 *
 * <p>The board is described twice, once from each side's point of view ("perspective"). From
 * white's view a white knight on g1 is "my knight on g1"; from black's view the same knight is
 * "their knight on g8" (the board is flipped vertically so each side sees itself at the bottom).
 * This lets one set of weights serve both colours, and the network always evaluates "the side to
 * move" against "the other side".
 *
 * <pre>
 *   index = relation * 384 + (pieceType - 1) * 64 + square
 *   relation: 0 = my piece, 1 = their piece      square: flipped (sq ^ 56) for black's view
 * </pre>
 *
 * A position has at most 32 active features per perspective out of 768: the input is <b>sparse</b>,
 * which is what makes NNUE cheap.
 */
public final class Features {

  public static final int COUNT = 768;

  private Features() {}

  /** Feature index of {@code piece} on {@code square}, seen from {@code perspective}'s side. */
  public static int index(int perspective, int piece, int square) {
    int relation = Piece.colour(piece) == perspective ? 0 : 1;
    int sq = perspective == Colour.WHITE ? square : square ^ 56;
    return relation * 384 + (Piece.type(piece) - 1) * 64 + sq;
  }

  /** The same feature seen from the other side (used by the trainer's data format). */
  public static int flip(int whiteFeature) {
    int relation = whiteFeature / 384;
    int rest = whiteFeature % 384;
    int type = rest / 64;
    int sq = rest % 64;
    return (relation ^ 1) * 384 + type * 64 + (sq ^ 56);
  }
}
