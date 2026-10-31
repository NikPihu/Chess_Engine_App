package io.github.nikpihu.chess.engine.fen.v1;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nikpihu.chess.engine.board.v1.Board;
import io.github.nikpihu.chess.engine.board.v1.Zobrist;
import io.github.nikpihu.chess.engine.testutil.RandomBoards;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** write(parse(fen)) must give back exactly the same text, for real and random positions. */
class FenRoundTripTest {

  static Stream<String> corpus() throws IOException {
    try (InputStream in = FenRoundTripTest.class.getResourceAsStream("/fen-corpus.txt")) {
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return text
          .lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .toList()
          .stream();
    }
  }

  @ParameterizedTest
  @MethodSource("corpus")
  void realPositionsRoundTrip(String fen) {
    Board board = FenParser.parse(fen);
    assertThat(FenWriter.write(board)).isEqualTo(fen);
    assertThat(board.zobristKey()).isEqualTo(Zobrist.compute(board));
  }

  @Test
  void thousandRandomPositionsRoundTrip() {
    for (long seed = 0; seed < 1000; seed++) {
      Board original = RandomBoards.create(seed);
      String fen = FenWriter.write(original);
      Board parsed = FenParser.parse(fen);

      assertThat(parsed).as("seed %d: %s", seed, fen).isEqualTo(original);
      assertThat(FenWriter.write(parsed)).isEqualTo(fen);
    }
  }

  @Test
  void fourFieldFenGetsDefaultClocks() {
    String fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -";
    assertThat(FenWriter.write(FenParser.parse(fen))).isEqualTo(fen + " 0 1");
  }
}
