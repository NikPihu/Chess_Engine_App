package com.gambyte.analysis;

import java.util.List;
import phase1.Board;
import phase1.Colour;
import phase1.Piece;
import phase2.Position;
import phase2.UciMoveParser;
import phase3.San;

/**
 * The free coach: turns engine facts into a plain-language sentence or two about a mistake.
 *
 * <p>It does not "understand" chess; it replays the engine's lines and reports what concretely
 * happens in them: material won or lost, or a forced mate. That covers most real mistakes at club
 * level (hanging pieces, missed tactics, walking into mate), and when nothing concrete shows up it
 * says what was better and how much the move cost. The optional AI coach builds on these facts.
 */
public final class CoachExplainer {

  private static final int[] VALUE = {0, 1, 3, 3, 5, 9, 0}; // by piece type (none, P, N, B, R, Q, K)

  private CoachExplainer() {}

  /**
   * Explains one move.
   *
   * @param fenBefore position before the move
   * @param played the move played (UCI)
   * @param bestLine engine's best line from fenBefore (UCI)
   * @param replyLine engine's best line after the played move (UCI), i.e. the punishment
   * @param mateAfterForOpponent mate-in-N for the opponent after the move, or null
   * @param mateBestForMover mate-in-N the mover had with the best line, or null
   * @param winLoss winning chances lost, in percentage points
   */
  public static String explain(
      String fenBefore,
      String played,
      List<String> bestLine,
      List<String> replyLine,
      Integer mateAfterForOpponent,
      Integer mateBestForMover,
      double winLoss) {
    Position before = Position.fromFen(fenBefore);
    int mover = before.sideToMove();
    String bestSan = bestLine.isEmpty() ? null : AnalysisService.toSan(fenBefore, bestLine.subList(0, 1)).getFirst();

    StringBuilder text = new StringBuilder();

    // 1. What the played move allowed.
    Position afterPlayed = Position.fromFen(fenBefore);
    afterPlayed.makeMove(UciMoveParser.parse(afterPlayed, played));
    String afterFen = afterPlayed.toFen();
    if (mateAfterForOpponent != null && mateAfterForOpponent > 0) {
      List<String> san = AnalysisService.toSan(afterFen, replyLine);
      text.append("This allows a forced mate in ").append(mateAfterForOpponent).append(": ")
          .append(String.join(" ", san.subList(0, Math.min(san.size(), 5)))).append(". ");
    } else {
      int lost = materialSwing(afterFen, replyLine, 4, mover);
      if (lost <= -2) {
        List<String> san = AnalysisService.toSan(afterFen, replyLine);
        text.append("This loses ").append(describe(-lost)).append(" after ")
            .append(String.join(" ", san.subList(0, Math.min(san.size(), 3)))).append(". ");
      }
    }

    // 2. What was missed.
    if (bestSan != null && !bestLine.getFirst().equals(played)) {
      if (mateBestForMover != null && mateBestForMover > 0) {
        text.append("You had a forced mate in ").append(mateBestForMover).append(" starting with ")
            .append(bestSan).append(". ");
      } else {
        int won = materialSwing(fenBefore, bestLine, 5, mover);
        if (won >= 2) {
          text.append("You missed ").append(bestSan).append(", which wins ").append(describe(won)).append(". ");
        } else {
          List<String> san = AnalysisService.toSan(fenBefore, bestLine);
          text.append(bestSan).append(" was stronger");
          if (san.size() > 1) text.append(" (").append(String.join(" ", san.subList(0, Math.min(san.size(), 4)))).append(")");
          text.append(". ");
        }
      }
    }
    if (text.isEmpty()) {
      text.append("A slip: this costs about ").append(Math.round(winLoss)).append("% winning chances. ");
    }
    return text.toString().trim();
  }

  /**
   * Material change for {@code side} after playing up to {@code plies} moves of {@code line}
   * from {@code fen}, ending after an opponent move when possible so a recapture is counted.
   */
  static int materialSwing(String fen, List<String> line, int plies, int side) {
    Position p = Position.fromFen(fen);
    int start = material(p.board(), side);
    int best = 0;
    for (int i = 0; i < Math.min(plies, line.size()); i++) {
      int move = UciMoveParser.parse(p, line.get(i));
      if (move == phase1.Move.NULL) break;
      p.makeMove(move);
      best = material(p.board(), side) - start;
    }
    return best;
  }

  private static int material(Board b, int side) {
    int score = 0;
    for (int sq = 0; sq < 64; sq++) {
      int piece = b.pieceAt(sq);
      if (piece == Piece.NONE) continue;
      int v = VALUE[Piece.type(piece)];
      score += Piece.colour(piece) == side ? v : -v;
    }
    return score;
  }

  private static String describe(int pawns) {
    if (pawns >= 9) return "the queen";
    if (pawns >= 5) return "a rook";
    if (pawns == 4) return "a piece and a pawn";
    if (pawns == 3) return "a piece";
    if (pawns == 2) return "the exchange";
    return pawns == 1 ? "a pawn" : pawns + " pawns";
  }

  /** "White"/"Black" for messages. */
  static String side(int colour) {
    return colour == Colour.WHITE ? "White" : "Black";
  }
}
