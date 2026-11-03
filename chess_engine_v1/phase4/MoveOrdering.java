package phase4;

import phase1.Board;
import phase1.Move;
import phase1.MoveFlag;
import phase1.Piece;
import phase2.MoveList;

/**
 * Move ordering v1: try the most promising moves first.
 *
 * <p>Alpha-beta prunes more the earlier it sees a good move. Order:
 *
 * <ol>
 *   <li>the <b>hash move</b> (best move stored in the transposition table),
 *   <li><b>captures</b> by MVV-LVA, "Most Valuable Victim, Least Valuable Attacker": PxQ before
 *       QxP,
 *   <li><b>promotions</b> (queen first),
 *   <li>everything else.
 * </ol>
 *
 * <p><b>DSA note — why not sort everything?</b> At most nodes the first or second move already
 * causes a cutoff, and the rest are never looked at. So instead of sorting the whole list (or
 * building a heap / priority queue), {@link #pickNext} does one step of selection sort just before
 * each move is tried: O(n) per move actually searched, and nothing wasted on the moves that are
 * pruned.
 */
public final class MoveOrdering {

  private static final int HASH_MOVE = 10_000_000;
  private static final int CAPTURE = 1_000_000;
  private static final int PROMOTION = 900_000;

  /** Victim values for MVV-LVA, indexed by piece type (none, P, N, B, R, Q, K). */
  private static final int[] VALUE = {0, 100, 320, 330, 500, 900, 2000};

  private MoveOrdering() {}

  /** Fills {@code scores[i]} for every move in the list. */
  public static void score(Board board, MoveList moves, int[] scores, int hashMove) {
    for (int i = 0; i < moves.size(); i++) {
      int move = moves.get(i);
      if (move == hashMove) {
        scores[i] = HASH_MOVE;
        continue;
      }
      int flag = Move.flag(move);
      int victim = board.pieceAt(Move.target(move));
      int score = 0;
      if (victim != Piece.NONE || flag == MoveFlag.EN_PASSANT) {
        int victimType = flag == MoveFlag.EN_PASSANT ? Piece.PAWN : Piece.type(victim);
        int attackerType = Piece.type(board.pieceAt(Move.start(move)));
        score = CAPTURE + VALUE[victimType] * 10 - VALUE[attackerType] / 10;
      }
      if (MoveFlag.isPromotion(flag)) {
        score += PROMOTION + VALUE[MoveFlag.promotionType(flag)];
      }
      scores[i] = score;
    }
  }

  /** Swaps the best remaining move into position {@code index} (one selection-sort step). */
  public static void pickNext(MoveList moves, int[] scores, int index) {
    int best = index;
    for (int i = index + 1; i < moves.size(); i++) {
      if (scores[i] > scores[best]) {
        best = i;
      }
    }
    if (best != index) {
      moves.swap(index, best);
      int t = scores[index];
      scores[index] = scores[best];
      scores[best] = t;
    }
  }
}
