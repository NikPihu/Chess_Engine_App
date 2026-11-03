package nnue.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import nnue.NetFile;
import nnue.Network;
import org.junit.jupiter.api.Test;

/**
 * Every trained network comes with a golden file written by train/export.py: 200 positions and the
 * integer evaluation Python computed from the exported weights. Java must give exactly the same
 * numbers, otherwise the engine would not play the network that was trained.
 */
class GoldenTest {

  @Test
  void javaEvaluatesExactlyLikePython() throws IOException {
    List<Path> goldens = findGoldenFiles();
    assumeTrue(!goldens.isEmpty(), "no trained network yet");
    for (Path golden : goldens) {
      Path netFile = Path.of(golden.toString().replace(".golden.txt", ".nnue"));
      Network net = NetFile.load(netFile);
      for (String line : Files.readAllLines(golden)) {
        String[] parts = line.split(";");
        int[] us = Arrays.stream(parts[0].split(",")).mapToInt(Integer::parseInt).toArray();
        int[] them = Arrays.stream(parts[1].split(",")).mapToInt(Integer::parseInt).toArray();
        int expected = Integer.parseInt(parts[2]);
        assertThat(net.evaluateFeatures(us, them))
            .as(golden.getFileName() + ": " + line)
            .isEqualTo(expected);
      }
    }
  }

  private static List<Path> findGoldenFiles() throws IOException {
    Stream<Path> nets =
        Files.isDirectory(Path.of("nets")) ? Files.list(Path.of("nets")) : Stream.empty();
    Path dev = Path.of("../tools/data");
    Stream<Path> devNets = Files.isDirectory(dev) ? Files.list(dev) : Stream.empty();
    return Stream.concat(nets, devNets)
        .filter(p -> p.toString().endsWith(".golden.txt"))
        .filter(p -> Files.exists(Path.of(p.toString().replace(".golden.txt", ".nnue"))))
        .toList();
  }
}
