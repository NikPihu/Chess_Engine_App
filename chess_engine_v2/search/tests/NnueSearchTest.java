package search.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import nnue.Network;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import phase1.Move;
import phase2.Position;
import phase3.SearchContext;
import phase3.SearchLimits;
import search.NnueSearch;

class NnueSearchTest {

  /**
   * Mates and free material are found by the search itself, so even a nonsense (random) network
   * must solve these. With no network the PeSTO fallback must too.
   */
  @ParameterizedTest(name = "{0} with {2} network")
  @CsvSource(
      delimiter = '|',
      value = {
        "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1 | d1d8 | random",
        "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1 | d1d8 | no",
        "7k/8/8/8/8/8/R7/1R4K1 w - - 0 1 | a2a7 | random",
        "4k3/8/8/3q4/8/8/8/3QK3 w - - 0 1 | d1d5 | no",
      })
  void findsForcedLines(String fen, String expected, String which) {
    Network net = which.trim().equals("random") ? Network.random(7) : null;
    NnueSearch search = new NnueSearch(net, which);
    Position pos = Position.fromFen(fen.trim());
    String before = pos.toFen();
    int best = search.bestMove(pos, SearchLimits.depth(6), SearchContext.silent());
    assertThat(Move.toUci(best)).isEqualTo(expected.trim());
    assertThat(pos.toFen()).as("position restored").isEqualTo(before);
  }

  @Test
  void reportsInfoAndRespectsTime() {
    List<String> lines = new ArrayList<>();
    long start = System.currentTimeMillis();
    new NnueSearch(Network.random(7), "random")
        .bestMove(
            Position.startPosition(), SearchLimits.moveTime(300), new SearchContext(lines::add));
    assertThat(System.currentTimeMillis() - start).isLessThan(1000);
    assertThat(lines.get(lines.size() - 1)).contains("score cp").contains(" pv ");
  }
}
