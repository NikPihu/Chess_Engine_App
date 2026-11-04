package nnue;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * The two hot loops of NNUE, written with Java's Vector API so they run as SIMD: one CPU
 * instruction works on 8–32 numbers at once. Only loaded when {@link Simd#ENABLED} (this class
 * needs the {@code jdk.incubator.vector} module). The maths is exactly the same as the plain loops
 * in {@link Network} and {@link Accumulator}.
 */
final class VectorKernels {

  private static final VectorSpecies<Integer> INT = IntVector.SPECIES_PREFERRED;

  /** Shorts with the same lane count as INT (for widening short -> int). */
  private static final VectorSpecies<Short> SHORT_FOR_INT =
      switch (INT.length()) {
        case 16 -> ShortVector.SPECIES_256;
        case 8 -> ShortVector.SPECIES_128;
        default -> ShortVector.SPECIES_64;
      };

  private static final VectorSpecies<Short> SHORT = ShortVector.SPECIES_PREFERRED;

  private VectorKernels() {}

  static int lanes() {
    return INT.length();
  }

  /** Sum over i of clamp(acc[i], 0, QA)² × w[offset + i]. */
  static int screluDot(short[] acc, short[] w, int offset) {
    IntVector sum = IntVector.zero(INT);
    for (int i = 0; i < Network.HIDDEN; i += INT.length()) {
      IntVector c =
          ((IntVector)
                  ShortVector.fromArray(SHORT_FOR_INT, acc, i)
                      .convertShape(VectorOperators.S2I, INT, 0))
              .max(0)
              .min(Network.QA);
      IntVector weight =
          (IntVector)
              ShortVector.fromArray(SHORT_FOR_INT, w, offset + i)
                  .convertShape(VectorOperators.S2I, INT, 0);
      sum = sum.add(c.mul(weight).mul(c));
    }
    return sum.reduceLanes(VectorOperators.ADD);
  }

  /** out = in - w[s1..] - w[s2..] + w[a1..] + w[a2..] (wrap-around short maths, like Java's). */
  static void update(short[] in, short[] out, short[] w, int s1, int s2, int a1, int a2) {
    for (int i = 0; i < Network.HIDDEN; i += SHORT.length()) {
      ShortVector.fromArray(SHORT, in, i)
          .sub(ShortVector.fromArray(SHORT, w, s1 + i))
          .sub(ShortVector.fromArray(SHORT, w, s2 + i))
          .add(ShortVector.fromArray(SHORT, w, a1 + i))
          .add(ShortVector.fromArray(SHORT, w, a2 + i))
          .intoArray(out, i);
    }
  }
}
