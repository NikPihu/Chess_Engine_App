package phase3.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import phase1.FenParser;
import phase1.Move;
import phase2.Position;
import phase2.UciMoveParser;
import phase3.Game;
import phase3.San;

class SanTest {

  @ParameterizedTest(name = "{1} = {2}")
  @CsvSource(
      delimiter = '|',
      value = {
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | e2e4 | e4",
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | g1f3 | Nf3",
        "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3 | e5d6 | exd6",
        "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq g3 0 2 | d8h4 | Qh4#",
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | e1g1 | O-O",
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | e1c1 | O-O-O",
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | e2a6 | Bxa6",
        "4k3/P7/8/8/8/8/8/4K3 w - - 0 1 | a7a8q | a8=Q+",
        "4k3/P7/8/8/8/8/8/4K3 w - - 0 1 | a7a8n | a8=N",
        "4k3/8/8/8/8/8/8/1N2KN2 w - - 0 1 | b1d2 | Nbd2",
        "4k3/8/8/R7/8/8/8/R3K3 w - - 0 1 | a1a3 | R1a3",
        "8/8/k7/8/4Q2Q/8/8/K6Q w - - 0 1 | h4e1 | Qh4e1",
      })
  void movesAreWrittenInStandardNotation(String fen, String uci, String san) {
    Position pos = Position.fromFen(fen.trim());
    int move = UciMoveParser.parse(pos, uci.trim());
    assertThat(San.toSan(pos, move)).isEqualTo(san.trim());
    assertThat(San.parse(pos, san.trim())).isEqualTo(move);
  }

  @Test
  void parseAcceptsZeroCastlingAndAnnotations() {
    Position pos =
        Position.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
    assertThat(Move.toUci(San.parse(pos, "0-0"))).isEqualTo("e1g1");
    assertThat(Move.toUci(San.parse(pos, "Bxa6!?"))).isEqualTo("e2a6");
    assertThat(San.parse(pos, "Ke3")).isEqualTo(Move.NULL);
  }

  @Test
  void pgnOfFoolsMate() {
    Game game = new Game();
    for (String m : new String[] {"f2f3", "e7e5", "g2g4", "d8h4"}) {
      game.play(m);
    }
    String pgn = game.toPgn(Map.of("White", "Fool", "Black", "Winner"));
    assertThat(pgn)
        .contains("[White \"Fool\"]")
        .contains("[Result \"0-1\"]")
        .endsWith("1. f3 e5 2. g4 Qh4# 0-1\n")
        .doesNotContain("[FEN");
  }

  @Test
  void pgnFromCustomPositionStartingWithBlack() {
    Game game = new Game("4k3/8/8/8/8/8/8/R3K3 b - - 0 40");
    game.play("e8d7");
    game.play("a1a7");
    assertThat(game.toPgn(Map.of()))
        .contains("[SetUp \"1\"]")
        .contains("[FEN \"4k3/8/8/8/8/8/8/R3K3 b - - 0 40\"]")
        .contains("40... Kd7 41. Ra7+ *");
    assertThat(game.startFen()).isNotEqualTo(FenParser.START_POSITION);
  }
}
