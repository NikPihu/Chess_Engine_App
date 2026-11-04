package datagen.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import nnue.NetFile;
import nnue.Network;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import phase1.FenParser;

/** The pieces that make automatic deployment safe. */
class DeployTest {

  @TempDir Path dir;

  /** Replacing best.nnue must be picked up by the next load (that is how a new gen goes live). */
  @Test
  void replacedNetworkFileIsReloaded() throws Exception {
    Path best = dir.resolve("best.nnue");
    Files.write(best, NetFile.write(Network.random(1)));
    Network first = NetFile.load(best);

    Path tmp = dir.resolve("best.nnue.tmp");
    Files.write(tmp, NetFile.write(Network.random(2)));
    Files.setLastModifiedTime(
        tmp, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
    Files.move(tmp, best, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    Network second = NetFile.load(best);

    assertThat(second).isNotSameAs(first);
    int[] start = startFeatures();
    assertThat(second.evaluateFeatures(start, start))
        .isNotEqualTo(first.evaluateFeatures(start, start));
    assertThat(NetFile.load(best)).isSameAs(second); // unchanged file: cached
  }

  @Test
  void blunderSuiteSolvesAnObviousPosition() throws Exception {
    Path epd = dir.resolve("suite.epd");
    Files.writeString(
        epd,
        "# test\n"
            + "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1 bm Rd8#; c0 \"back rank\";\n"
            + "4k3/8/8/3q4/8/8/8/3QK3 w - - 0 1 bm Qxd5; c0 \"free queen\";\n");
    var lines = datagen.BlunderSuite.read(epd);
    assertThat(lines).hasSize(2);
    int[] r = datagen.BlunderSuite.solve(lines, "v2", null, 5000, 2);
    assertThat(r[0]).isEqualTo(2);
    assertThat(r[1]).isEqualTo(2);
  }

  private static int[] startFeatures() {
    var board = FenParser.parse(FenParser.START_POSITION);
    int[] f = new int[32];
    int n = 0;
    for (int sq = 0; sq < 64; sq++) {
      if (board.pieceAt(sq) != 0) {
        f[n++] = nnue.Features.index(0, board.pieceAt(sq), sq);
      }
    }
    return f;
  }
}
