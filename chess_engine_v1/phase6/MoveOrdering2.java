package phase6;

import phase1.Board;
import phase1.Move;
import phase1.MoveFlag;
import phase1.Piece;
import phase2.MoveList;

/**
 * Move ordering v2 = v1 (hash move, MVV-LVA captures, promotions) plus, for quiet moves:
 *
 * <ol>
 *   <li>the two <b>killer</b> moves of this ply ({@link Killers}),
 *   <li>then the rest by <b>history</b> score ({@link History}).
 * </ol>
 *
 * Uses the same lazy selection as v1 ({@code phase4.MoveOrdering.pickNext}).
 */
public final class MoveOrdering2 {

  private static final int HASH_MOVE = 10_000_000;
  private static final int CAPTURE = 1_000_000;
  private static final int PROMOTION = 900_000;
  private static final int KILLER_1 = 800_000;
  private static final int KILLER_2 = 790_000;

  private static final int[] VALUE = {0, 100, 320, 330, 500, 900, 2000};

  private MoveOrdering2() {}

  public static boolean isQuiet(Board board, int move) {
    int flag = Move.flag(move);
    return board.pieceAt(Move.target(move)) == Piece.NONE
        && flag != MoveFlag.EN_PASSANT
        && !MoveFlag.isPromotion(flag);
  }

  public static void score(
      Board board,
      MoveList moves,
      int[] scores,
      int hashMove,
      int killer1,
      int killer2,
      History history,
      int features) {
    int side = board.sideToMove();
    boolean useKillers = Features.on(features, Features.KILLERS);
    boolean useHistory = Features.on(features, Features.HISTORY);
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
      } else if (MoveFlag.isPromotion(flag)) {
        score = PROMOTION + VALUE[MoveFlag.promotionType(flag)];
      } else if (useKillers && move == killer1) {
        score = KILLER_1;
      } else if (useKillers && move == killer2) {
        score = KILLER_2;
      } else if (useHistory) {
        score = history.score(side, Move.start(move), Move.target(move));
      }
      if (MoveFlag.isPromotion(flag) && score >= CAPTURE) {
        score += VALUE[MoveFlag.promotionType(flag)];
      }
      scores[i] = score;
    }
  }
}
