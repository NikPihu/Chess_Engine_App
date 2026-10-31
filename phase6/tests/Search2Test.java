package phase6.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import phase1.FenParser;
import phase1.Move;
import phase2.Position;
import phase3.SearchContext;
import phase3.SearchLimits;
import phase6.Evaluation2;
import phase6.Features;
import phase6.Search2;

class Search2Test {

  @Test
  void featureTextRoundTrips() {
    assertThat(Features.parse("all")).isEqualTo(Features.ALL);
    assertThat(Features.parse("none")).isZero();
    assertThat(Features.parse("pvs,nmp")).isEqualTo(Features.PVS | Features.NULL_MOVE);
    assertThat(Features.parse("all,-nmp")).isEqualTo(Features.ALL & ~Features.NULL_MOVE);
    assertThat(Features.toText(Features.PVS | Features.LMR)).isEqualTo("pvs,lmr");
    assertThat(Features.parse(Features.toText(Features.ALL & ~Features.MOBILITY)))
        .isEqualTo(Features.ALL & ~Features.MOBILITY);
    assertThatThrownBy(() -> Features.parse("turbo")).hasMessageContaining("Unknown feature");
  }

  /** v2 must solve the same tactics as v1, with every feature on and with every feature off. */
  @ParameterizedTest(name = "{0} with {2}")
  @CsvSource(
      delimiter = '|',
      value = {
        "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1 | d1d8 | all",
        "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1 | d1d8 | none",
        "7k/8/8/8/8/8/R7/1R4K1 w - - 0 1 | a2a7 | all",
        "4k3/8/8/3q4/8/8/8/3QK3 w - - 0 1 | d1d5 | all",
        "4k3/8/8/3q4/8/8/8/3QK3 w - - 0 1 | d1d5 | none",
      })
  void solvesTactics(String fen, String expected, String features) {
    Search2 v2 = new Search2();
    v2.setFeatures(Features.parse(features.trim()));
    Position pos = Position.fromFen(fen.trim());
    String before = pos.toFen();
    int best = v2.bestMove(pos, SearchLimits.depth(6), SearchContext.silent());
    assertThat(Move.toUci(best)).isEqualTo(expected.trim());
    assertThat(pos.toFen()).isEqualTo(before);
  }

  @Test
  void reportsMateScores() {
    List<String> lines = new ArrayList<>();
    new Search2()
        .bestMove(
            Position.fromFen("7k/8/8/8/8/8/R7/1R4K1 w - - 0 1"),
            SearchLimits.depth(6),
            new SearchContext(lines::add));
    assertThat(lines.get(lines.size() - 1)).contains("score mate 2");
  }

  @Test
  void searchesDeeperThanV1InTheSameTime() {
    String kiwipete = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
    int v1 = depthReached(new phase4.Search(), kiwipete);
    int v2 = depthReached(new Search2(), kiwipete);
    assertThat(v2).isGreaterThan(v1);
  }

  private static int depthReached(phase3.Searcher engine, String fen) {
    List<String> lines = new ArrayList<>();
    engine.bestMove(
        Position.fromFen(fen), SearchLimits.moveTime(700), new SearchContext(lines::add));
    String last = lines.get(lines.size() - 1);
    return Integer.parseInt(last.split(" ")[2]);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
        "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
        "4k3/8/8/8/8/8/4P3/R3K3 w - - 0 1",
      })
  void evaluationV2IsSymmetric(String fen) {
    assertThat(Evaluation2.evaluate(FenParser.parse(mirror(fen)), Features.ALL))
        .isEqualTo(Evaluation2.evaluate(FenParser.parse(fen), Features.ALL));
  }

  @Test
  void evaluationV2WithNoFeaturesEqualsV1() {
    String fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
    assertThat(Evaluation2.evaluate(FenParser.parse(fen), Features.NONE))
        .isEqualTo(phase4.Evaluation.evaluate(FenParser.parse(fen)));
  }

  @Test
  void passedPawnIsWorthMoreThanBlockedPawn() {
    int passed =
        Evaluation2.evaluate(FenParser.parse("4k3/8/8/4P3/8/8/8/4K3 w - - 0 1"), Features.ALL);
    int blocked =
        Evaluation2.evaluate(FenParser.parse("4k3/4p3/8/4P3/8/8/8/4K3 w - - 0 1"), Features.ALL);
    assertThat(passed).isGreaterThan(blocked);
  }

  static String mirror(String fen) {
    String[] f = fen.split(" ");
    String[] ranks = f[0].split("/");
    StringBuilder placement = new StringBuilder();
    for (int i = 7; i >= 0; i--) {
      for (char c : ranks[i].toCharArray()) {
        placement.append(
            Character.isLetter(c)
                ? (Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c))
                : c);
      }
      if (i > 0) {
        placement.append('/');
      }
    }
    StringBuilder castling = new StringBuilder();
    for (char c : f[2].toCharArray()) {
      castling.append(
          c == '-'
              ? c
              : Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
    }
    return placement + " " + (f[1].equals("w") ? "b" : "w") + " " + castling + " - 0 1";
  }
}
