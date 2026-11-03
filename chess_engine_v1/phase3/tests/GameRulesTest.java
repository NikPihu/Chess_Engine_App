package phase3.tests;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import phase1.FenParser;
import phase3.Game;
import phase3.GameResult;
import phase3.GameRules;

class GameRulesTest {

  private static Game play(String fen, String... moves) {
    Game game = new Game(fen);
    for (String m : moves) {
      assertThat(game.play(m)).as(m).isTrue();
    }
    return game;
  }

  @Test
  void foolsMateIsCheckmate() {
    Game game = play(FenParser.START_POSITION, "f2f3", "e7e5", "g2g4", "d8h4");
    assertThat(game.result()).isEqualTo(GameResult.BLACK_WINS);
  }

  @Test
  void stalemate() {
    assertThat(new Game("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1").result()).isEqualTo(GameResult.STALEMATE);
  }

  @Test
  void fiftyMoveRule() {
    assertThat(new Game("4k3/8/8/8/8/8/8/R3K3 w - - 100 80").result())
        .isEqualTo(GameResult.FIFTY_MOVE_RULE);
    assertThat(new Game("4k3/8/8/8/8/8/8/R3K3 w - - 99 80").result()).isEqualTo(GameResult.ONGOING);
  }

  @Test
  void threefoldRepetitionByShufflingKnights() {
    String[] shuffle = {"g1f3", "g8f6", "f3g1", "f6g8"};
    Game game = play(FenParser.START_POSITION, shuffle);
    assertThat(GameRules.repetitionCount(game.position())).isEqualTo(2);
    assertThat(game.result()).isEqualTo(GameResult.ONGOING);

    for (String m : shuffle) {
      game.play(m);
    }
    assertThat(GameRules.repetitionCount(game.position())).isEqualTo(3);
    assertThat(game.result()).isEqualTo(GameResult.THREEFOLD_REPETITION);
  }

  @Test
  void captureResetsRepetitionWindow() {
    // Same piece placement again, but castling rights differ -> a different position.
    Game game = play(FenParser.START_POSITION, "e2e4", "e7e5", "e1e2", "e8e7", "e2e1", "e7e8");
    assertThat(GameRules.repetitionCount(game.position())).isEqualTo(1);
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "4k3/8/8/8/8/8/8/4K3 w - - 0 1, true",
    "4k3/8/8/8/8/8/8/2B1K3 w - - 0 1, true",
    "4k3/8/8/8/8/8/8/1N2K3 w - - 0 1, true",
    "5b2/4k3/8/8/8/8/8/2B1K3 w - - 0 1, true",
    "2b5/4k3/8/8/8/8/8/2B1K3 w - - 0 1, false",
    "4k3/8/8/8/8/8/8/1NN1K3 w - - 0 1, false",
    "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1, false",
  })
  void insufficientMaterial(String fen, boolean expected) {
    assertThat(GameRules.isInsufficientMaterial(FenParser.parse(fen))).isEqualTo(expected);
  }
}
