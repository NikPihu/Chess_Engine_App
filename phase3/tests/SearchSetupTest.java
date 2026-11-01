package phase3.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;
import phase1.Move;
import phase3.Game;
import phase3.RandomPlayer;
import phase3.SearchContext;
import phase3.SearchLimits;

class SearchSetupTest {

  @Test
  void parsesGoCommands() {
    SearchLimits a =
        SearchLimits.parse("go wtime 60000 btime 50000 winc 1000 binc 900".split(" "), 1);
    assertThat(a.whiteTimeMs).isEqualTo(60000);
    assertThat(a.blackTimeMs).isEqualTo(50000);
    assertThat(a.whiteIncrementMs).isEqualTo(1000);
    assertThat(a.blackIncrementMs).isEqualTo(900);
    assertThat(a.hasClock()).isTrue();

    SearchLimits b = SearchLimits.parse("go depth 7 movetime 500".split(" "), 1);
    assertThat(b.depth).isEqualTo(7);
    assertThat(b.moveTimeMs).isEqualTo(500);
    assertThat(b.hasClock()).isFalse();

    assertThat(SearchLimits.parse("go infinite".split(" "), 1).infinite).isTrue();
  }

  @Test
  void randomPlayersFinishWholeGamesLegally() {
    RandomPlayer player = new RandomPlayer(new Random(5));
    for (int g = 0; g < 50; g++) {
      Game game = new Game();
      while (!game.result().isOver()) {
        int move =
            player.bestMove(game.copyOfPosition(), SearchLimits.depth(1), SearchContext.silent());
        assertThat(move).isNotEqualTo(Move.NULL);
        game.play(move);
      }
      assertThat(game.toPgn(java.util.Map.of())).contains(game.result().pgn);
    }
  }
}
