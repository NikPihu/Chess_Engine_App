package phase4;

import phase1.Bitboards;
import phase1.Board;
import phase1.Colour;
import phase1.Piece;

/**
 * Evaluation v1: "how good is this position?" in centipawns (100 = one pawn), from the point of
 * view of the side to move (positive = good for the side to move). That convention is what negamax
 * needs.
 *
 * <p><b>Tapered evaluation.</b> We add up the middlegame score and the endgame score of every piece
 * separately ({@link PestoTables}), then blend them by the game phase:
 *
 * <pre>
 *   phase = 1 per knight/bishop + 2 per rook + 4 per queen   (24 at the start, 0 with only pawns)
 *   score = (mg * phase + eg * (24 - phase)) / 24
 * </pre>
 *
 * So the king slowly changes from "hide behind pawns" to "walk to the centre" as pieces come off.
 */
public final class Evaluation {

  private Evaluation() {}

  public static int evaluate(Board board) {
    int mg = 0;
    int eg = 0;
    int phase = 0;
    for (int piece : Piece.ALL) {
      long bb = board.pieces(piece);
      int sign = Piece.colour(piece) == Colour.WHITE ? 1 : -1;
      int[] mgTable = PestoTables.MG[piece];
      int[] egTable = PestoTables.EG[piece];
      while (bb != 0) {
        int sq = Bitboards.lowestSquare(bb);
        bb = Bitboards.withoutLowest(bb);
        mg += sign * mgTable[sq];
        eg += sign * egTable[sq];
        phase += PestoTables.PHASE_WEIGHT[Piece.type(piece) - 1];
      }
    }
    phase = Math.min(phase, 24); // early promotions can push it above 24
    int whiteScore = (mg * phase + eg * (24 - phase)) / 24;
    return board.sideToMove() == Colour.WHITE ? whiteScore : -whiteScore;
  }
}
