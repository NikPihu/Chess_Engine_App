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
    int from = Move.start(move);
    int to = Move.target(move);
    int flag = Move.flag(move);
    int piece = board.pieceAt(from);
    int us = Piece.colour(piece);

    remove(net, piece, from);
    if (flag == MoveFlag.EN_PASSANT) {
      int captureSquare = to + (us == Colour.WHITE ? -8 : 8);
      remove(net, board.pieceAt(captureSquare), captureSquare);
    } else if (board.pieceAt(to) != Piece.NONE) {
      remove(net, board.pieceAt(to), to);
    }
    int placed = MoveFlag.isPromotion(flag) ? Piece.make(us, MoveFlag.promotionType(flag)) : piece;
    add(net, placed, to);

    if (flag == MoveFlag.CASTLE) {
      int rook = Piece.make(us, Piece.ROOK);
      switch (to) {
        case Square.G1 -> moveRook(net, rook, Square.H1, Square.F1);
        case Square.C1 -> moveRook(net, rook, Square.A1, Square.D1);
        case Square.G8 -> moveRook(net, rook, Square.H8, Square.F8);
        default -> moveRook(net, rook, Square.A8, Square.D8);
      }
    }
  }

  /** Evaluation in centipawns from the point of view of {@code sideToMove}. */
  public int evaluate(Network net, int sideToMove) {
    return net.evaluate(values[sideToMove], values[Colour.opposite(sideToMove)]);
  }

  private void moveRook(Network net, int rook, int from, int to) {
    remove(net, rook, from);
    add(net, rook, to);
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

  private void remove(Network net, int piece, int square) {
    for (int p = 0; p < 2; p++) {
      int offset = Features.index(p, piece, square) * Network.HIDDEN;
      short[] v = values[p];
      short[] w = net.featureWeights;
      for (int i = 0; i < Network.HIDDEN; i++) {
        v[i] -= w[offset + i];
      }
    }
  }
}
