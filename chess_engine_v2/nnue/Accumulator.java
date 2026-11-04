package nnue;

import phase1.Bitboards;
import phase1.Board;
import phase1.Colour;
import phase1.Move;
import phase1.MoveFlag;
import phase1.Piece;
import phase1.Square;

/**
 * The network's hidden layer for one position, from both perspectives: {@code
 * values[perspective][0..256)}.
 *
 * <p><b>The NNUE trick.</b> A move changes only 2–4 features (a piece leaves a square, arrives on
 * another, maybe a capture or the castling rook). So instead of summing ~32 weight columns for
 * every position, we copy the parent's accumulator and add/subtract just those few columns. This is
 * the "efficiently updatable" in NNUE.
 *
 * <p>{@link #refresh} computes it from scratch (used at the root of a search and by tests to prove
 * the incremental updates are exact).
 */
public final class Accumulator {

  final short[][] values = new short[2][Network.HIDDEN];

  /** Computes both perspectives from scratch. */
  public void refresh(Network net, Board board) {
    for (int p = 0; p < 2; p++) {
      System.arraycopy(net.featureBias, 0, values[p], 0, Network.HIDDEN);
    }
    long occupied = board.occupied();
    while (occupied != 0) {
      int sq = Bitboards.lowestSquare(occupied);
      occupied = Bitboards.withoutLowest(occupied);
      add(net, board.pieceAt(sq), sq);
    }
  }

  public void copyFrom(Accumulator other) {
    System.arraycopy(other.values[0], 0, values[0], 0, Network.HIDDEN);
    System.arraycopy(other.values[1], 0, values[1], 0, Network.HIDDEN);
  }

  /**
   * Updates this accumulator (already a copy of the parent's) for {@code move}, which is about to
   * be played on {@code board}. Call before {@code Position.makeMove}.
   */
  public void applyMove(Network net, Board board, int move) {
    update(net, board, move, this);
  }

  /**
   * Sets this accumulator to {@code parent}'s, updated for {@code move} (about to be played on
   * {@code board}). Call before {@code Position.makeMove}.
   *
   * <p>One fused pass per perspective: {@code child = parent - removed1 - removed2 + added1 +
   * added2}. A quiet move only has one of each; the unused slots point at the network's all-zero
   * column, so every move runs the same branch-free loop (fast, and SIMD-friendly).
   */
  public void update(Network net, Board board, int move, Accumulator parent) {
    int from = Move.start(move);
    int to = Move.target(move);
    int flag = Move.flag(move);
    int piece = board.pieceAt(from);
    int us = Piece.colour(piece);
    int placed = MoveFlag.isPromotion(flag) ? Piece.make(us, MoveFlag.promotionType(flag)) : piece;

    int sub2Piece = Piece.NONE;
    int sub2Square = 0;
    int add2Piece = Piece.NONE;
    int add2Square = 0;
    if (flag == MoveFlag.EN_PASSANT) {
      sub2Square = to + (us == Colour.WHITE ? -8 : 8);
      sub2Piece = board.pieceAt(sub2Square);
    } else if (flag == MoveFlag.CASTLE) {
      int rook = Piece.make(us, Piece.ROOK);
      sub2Piece = rook;
      add2Piece = rook;
      switch (to) {
        case Square.G1 -> {
          sub2Square = Square.H1;
          add2Square = Square.F1;
        }
        case Square.C1 -> {
          sub2Square = Square.A1;
          add2Square = Square.D1;
        }
        case Square.G8 -> {
          sub2Square = Square.H8;
          add2Square = Square.F8;
        }
        default -> {
          sub2Square = Square.A8;
          add2Square = Square.D8;
        }
      }
    } else if (board.pieceAt(to) != Piece.NONE) {
      sub2Piece = board.pieceAt(to);
      sub2Square = to;
    }

    short[] w = net.featureWeights;
    for (int p = 0; p < 2; p++) {
      int s1 = offset(p, piece, from);
      int s2 = offset(p, sub2Piece, sub2Square);
      int a1 = offset(p, placed, to);
      int a2 = offset(p, add2Piece, add2Square);
      short[] in = parent.values[p];
      short[] out = values[p];
      if (Simd.ENABLED) {
        VectorKernels.update(in, out, w, s1, s2, a1, a2);
      } else {
        for (int i = 0; i < Network.HIDDEN; i++) {
          out[i] = (short) (in[i] - w[s1 + i] - w[s2 + i] + w[a1 + i] + w[a2 + i]);
        }
      }
    }
  }

  /** Start of a feature's weight column; the all-zero column for "no piece". */
  private static int offset(int perspective, int piece, int square) {
    int feature = piece == Piece.NONE ? Network.ZERO : Features.index(perspective, piece, square);
    return feature * Network.HIDDEN;
  }

  /** Evaluation in centipawns from the point of view of {@code sideToMove}. */
  public int evaluate(Network net, int sideToMove) {
    return net.evaluate(values[sideToMove], values[Colour.opposite(sideToMove)]);
  }

  private void add(Network net, int piece, int square) {
    for (int p = 0; p < 2; p++) {
      int offset = Features.index(p, piece, square) * Network.HIDDEN;
      short[] v = values[p];
      short[] w = net.featureWeights;
      for (int i = 0; i < Network.HIDDEN; i++) {
        v[i] += w[offset + i];
      }
    }
  }
}
