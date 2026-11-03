package phase2;

import phase1.Move;

/**
 * Perft ("performance test"): counts every move sequence of a given length from a position.
 *
 * <p>For well-known positions the correct counts are published
 * (chessprogramming.org/Perft_Results). If our count matches at depth 5 or 6, the move generator
 * and make/unmake are almost certainly right: millions of positions, every special rule exercised.
 * If it doesn't match, {@link #divide} shows the count per first move, so you can narrow down which
 * move sequence is wrong.
 *
 * <p>It is a plain depth-first search over the game tree. "Bulk counting": at depth 1 we don't play
 * the moves, we just count them.
 */
public final class Perft {

  private final Position position;
  private final MoveList[] lists = new MoveList[64];

  public Perft(Position position) {
    this.position = position;
    for (int i = 0; i < lists.length; i++) {
      lists[i] = new MoveList();
    }
  }

  public long count(int depth) {
    return count(depth, 0);
  }

  private long count(int depth, int ply) {
    if (depth == 0) {
      return 1;
    }
    MoveList moves = lists[ply];
    MoveGenerator.generate(position, moves);
    if (depth == 1) {
      return moves.size();
    }
    long nodes = 0;
    for (int i = 0; i < moves.size(); i++) {
      int move = moves.get(i);
      position.makeMove(move);
      nodes += count(depth - 1, ply + 1);
      position.unmakeMove(move);
    }
    return nodes;
  }

  /** Per-first-move counts, one line each ("e2e4: 9771"), then the total. */
  public String divide(int depth) {
    StringBuilder sb = new StringBuilder();
    MoveList moves = new MoveList();
    MoveGenerator.generate(position, moves);
    long total = 0;
    for (int i = 0; i < moves.size(); i++) {
      int move = moves.get(i);
      position.makeMove(move);
      long nodes = depth <= 1 ? 1 : count(depth - 1, 1);
      position.unmakeMove(move);
      total += nodes;
      sb.append(Move.toUci(move)).append(": ").append(nodes).append('\n');
    }
    sb.append("\nNodes searched: ").append(total).append('\n');
    return sb.toString();
  }
}
