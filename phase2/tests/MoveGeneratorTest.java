package phase2.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;
import phase1.Colour;
import phase1.Move;
import phase1.MoveFlag;
import phase1.Piece;
import phase1.Zobrist;
import phase2.MoveGenerator;
import phase2.MoveList;
import phase2.Position;
import phase2.UciMoveParser;

/** Properties checked over thousands of positions reached by random play. */
class MoveGeneratorTest {

  private static final String[] STARTS = {
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
    "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
  };

  @Test
  void randomGamesKeepEveryInvariant() {
    Random random = new Random(2);
    MoveList moves = new MoveList();
    MoveList captures = new MoveList();
    for (int game = 0; game < 300; game++) {
      Position pos = Position.fromFen(STARTS[game % STARTS.length]);
      for (int ply = 0; ply < 120; ply++) {
        MoveGenerator.generate(pos, moves);
        if (moves.size() == 0) {
          break;
        }
        checkNoMoveLeavesKingInCheck(pos, moves);
        checkCapturesAreTheRightSubset(pos, moves, captures);

        String fenBefore = pos.toFen();
        int move = moves.get(random.nextInt(moves.size()));
        pos.makeMove(move);
        assertThat(pos.key()).isEqualTo(Zobrist.compute(pos.board()));
        pos.unmakeMove(move);
        assertThat(pos.toFen()).isEqualTo(fenBefore);
        pos.makeMove(move);
      }
    }
  }

  private static void checkNoMoveLeavesKingInCheck(Position pos, MoveList moves) {
    int us = pos.sideToMove();
    for (int i = 0; i < moves.size(); i++) {
      pos.makeMove(moves.get(i));
      int ourKing = pos.board().kingSquare(us);
      assertThat(pos.isAttacked(ourKing, Colour.opposite(us)))
          .as("%s leaves the king in check", Move.toUci(moves.get(i)))
          .isFalse();
      pos.unmakeMove(moves.get(i));
    }
  }

  /** Captures-only mode = every capture/en passant + queen promotions, nothing else. */
  private static void checkCapturesAreTheRightSubset(
      Position pos, MoveList all, MoveList captures) {
    MoveGenerator.generateCaptures(pos, captures);
    int expected = 0;
    for (int i = 0; i < all.size(); i++) {
      int m = all.get(i);
      int flag = Move.flag(m);
      boolean capture = pos.board().pieceAt(Move.target(m)) != Piece.NONE;
      boolean wanted =
          flag == MoveFlag.EN_PASSANT
              || flag == MoveFlag.PROMOTE_QUEEN
              || (capture && !MoveFlag.isPromotion(flag));
      if (wanted) {
        expected++;
        assertThat(captures.contains(m)).as(Move.toUci(m)).isTrue();
      }
    }
    assertThat(captures.size()).isEqualTo(expected);
  }

  @Test
  void parsesUciMovesIncludingSpecialOnes() {
    Position pos =
        Position.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
    assertThat(Move.flag(UciMoveParser.parse(pos, "e1g1"))).isEqualTo(MoveFlag.CASTLE);
    assertThat(Move.flag(UciMoveParser.parse(pos, "a2a4"))).isEqualTo(MoveFlag.PAWN_DOUBLE_PUSH);
    assertThat(UciMoveParser.parse(pos, "e1e3")).isEqualTo(Move.NULL); // illegal
  }

  @Test
  void nullMoveIsUndoneExactly() {
    Position pos = Position.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");
    String before = pos.toFen();
    pos.makeNullMove();
    assertThat(pos.sideToMove()).isEqualTo(Colour.WHITE);
    assertThat(pos.key()).isEqualTo(Zobrist.compute(pos.board()));
    pos.unmakeNullMove();
    assertThat(pos.toFen()).isEqualTo(before);
  }
}
