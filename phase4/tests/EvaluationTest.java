package phase4.tests;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import phase1.FenParser;
import phase1.Move;
import phase4.Evaluation;
import phase4.Search;
import phase4.TranspositionTable;

class EvaluationTest {

  @Test
  void startPositionIsEqual() {
    assertThat(Evaluation.evaluate(FenParser.parse(FenParser.START_POSITION))).isZero();
  }

  @Test
  void extraQueenIsWinning() {
    int score = Evaluation.evaluate(FenParser.parse("4k3/8/8/8/8/8/8/3QK3 w - - 0 1"));
    assertThat(score).isGreaterThan(800);
    int blackView = Evaluation.evaluate(FenParser.parse("4k3/8/8/8/8/8/8/3QK3 b - - 0 1"));
    assertThat(blackView).isEqualTo(-score);
  }

  /** Swapping colours (and flipping the board) must give exactly the same score. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
        "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
      })
  void evaluationIsSymmetric(String fen) {
    assertThat(Evaluation.evaluate(FenParser.parse(mirror(fen))))
        .isEqualTo(Evaluation.evaluate(FenParser.parse(fen)));
  }

  /** Flip ranks, swap piece colours and the side to move. */
  static String mirror(String fen) {
    String[] f = fen.split(" ");
    String[] ranks = f[0].split("/");
    StringBuilder placement = new StringBuilder();
    for (int i = 7; i >= 0; i--) {
      for (char c : ranks[i].toCharArray()) {
        placement.append(Character.isLetter(c) ? swapCase(c) : c);
      }
      if (i > 0) {
        placement.append('/');
      }
    }
    String side = f[1].equals("w") ? "b" : "w";
    String castling = f[2].equals("-") ? "-" : swapCaseAll(f[2]);
    return placement + " " + side + " " + castling + " - 0 1";
  }

  private static char swapCase(char c) {
    return Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c);
  }

  private static String swapCaseAll(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      sb.append(swapCase(c));
    }
    return sb.toString();
  }

  @Test
  void transpositionTableStoresAndAdjustsMateScores() {
    TranspositionTable tt = new TranspositionTable(1);
    tt.newSearch();
    int move = Move.of(12, 28);
    // "Mate in 3 plies from the root" found at ply 1 = mate in 2 plies from that node.
    tt.store(42L, move, Search.MATE - 3, 5, TranspositionTable.EXACT, 1);
    long entry = tt.probe(42L);
    assertThat(TranspositionTable.move(entry)).isEqualTo(move);
    assertThat(TranspositionTable.depth(entry)).isEqualTo(5);
    assertThat(TranspositionTable.bound(entry)).isEqualTo(TranspositionTable.EXACT);
    // Reached again at ply 4: still mate 2 plies from the node -> MATE - 6 from the root.
    assertThat(TranspositionTable.score(entry, 4)).isEqualTo(Search.MATE - 6);
    assertThat(tt.probe(43L)).isEqualTo(TranspositionTable.MISS);
  }

  @Test
  void transpositionTableKeepsDeeperEntryFromSameSearch() {
    TranspositionTable tt = new TranspositionTable(1);
    tt.newSearch();
    long a = 5L;
    long b = 5L + (1L << 40); // same slot, different key
    tt.store(a, 1, 10, 8, TranspositionTable.EXACT, 0);
    tt.store(b, 2, 20, 3, TranspositionTable.EXACT, 0);
    assertThat(tt.probe(a)).isNotEqualTo(TranspositionTable.MISS);
    tt.newSearch(); // next search: the old entry may now be replaced
    tt.store(b, 2, 20, 3, TranspositionTable.EXACT, 0);
    assertThat(tt.probe(b)).isNotEqualTo(TranspositionTable.MISS);
  }
}
