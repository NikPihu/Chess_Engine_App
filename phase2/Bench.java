package phase2;

/**
 * Move-generation speed benchmark: runs perft on fixed positions and reports nodes per second.
 *
 * <p>This is our first performance baseline. Whenever move generation changes, run {@code bench}
 * again: the node count must stay identical (correctness) and nodes/second shows speed.
 */
public final class Bench {

  private static final String[][] POSITIONS = {
    {"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "5"},
    {"r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", "4"},
    {"8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", "5"},
    {"r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10", "4"},
  };

  private Bench() {}

  /** Runs the benchmark and returns a printable report. */
  public static String run() {
    // Warm-up so the JIT compiler has optimised the code before we time it.
    new Perft(Position.startPosition()).count(4);

    StringBuilder sb = new StringBuilder();
    long totalNodes = 0;
    long start = System.nanoTime();
    for (String[] p : POSITIONS) {
      long nodes = new Perft(Position.fromFen(p[0])).count(Integer.parseInt(p[1]));
      totalNodes += nodes;
      sb.append(String.format("depth %s  %,13d nodes  %s%n", p[1], nodes, p[0]));
    }
    double seconds = (System.nanoTime() - start) / 1e9;
    sb.append(
        String.format(
            "%nTotal: %,d nodes in %.2f s = %,.0f nodes/second%n",
            totalNodes, seconds, totalNodes / seconds));
    return sb.toString();
  }
}
