package phase1.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import phase1.Board;
import phase1.FenParser;
import phase1.FenWriter;
import phase1.Zobrist;

/** write(parse(fen)) must give back exactly the same text, for real and random positions. */
class FenRoundTripTest {

  /** Lines of phase1/tests/fen-corpus.txt (tests run from the project root folder). */
  static Stream<String> corpus() throws IOException {
    return Files.readAllLines(Path.of("phase1", "tests", "fen-corpus.txt"), StandardCharsets.UTF_8)
        .stream()
        .map(String::trim)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"));
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
