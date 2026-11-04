package nnue;

import java.util.Random;

/**
 * The trained network (weights only, no position state). Shape:
 *
 * <pre>
 *   768 features ──► 256 (feature transformer, per perspective)  ┐
 *                                                                ├─► SCReLU ─► 512 ─► 1 output
 *   768 features ──► 256 (same weights, other perspective)       ┘
 * </pre>
 *
 * <ul>
 *   <li><b>Feature transformer:</b> one 256-number column per feature. A position's hidden layer is
 *       just the sum of the columns of its ~32 active features (plus a bias). That sum is kept up
 *       to date move by move in an {@link Accumulator}, so it is almost never computed from
 *       scratch.
 *   <li><b>SCReLU</b> ("squared clipped ReLU"): clamp each number to [0, 1], then square it.
 *   <li><b>Output layer:</b> a weighted sum of the side to move's 256 values and the opponent's 256
 *       values, giving one number, the evaluation.
 * </ul>
 *
 * <p><b>Quantisation.</b> Training uses floating point; the engine uses integers, which are much
 * faster: feature-transformer weights are stored ×{@link #QA} (255) and output weights ×{@link #QB}
 * (64), all as {@code short}. The maths is arranged so that the integer result equals the float
 * result times a known constant, then converted to centipawns with {@link #SCALE}.
 */
public final class Network {

  public static final int HIDDEN = 256;
  public static final int QA = 255;
  public static final int QB = 64;

  /** Network output 1.0 = 400 centipawns (the same 400 as the training loss's sigmoid). */
  public static final int SCALE = 400;

  /**
   * featureWeights[feature * HIDDEN + i], plus one extra all-zero column at index {@link #ZERO} so
   * a move update can always add/subtract a fixed number of columns without branches.
   */
  final short[] featureWeights;

  /** Index of the all-zero "no feature" column. */
  static final int ZERO = Features.COUNT;

  final short[] featureBias;

  /** outputWeights[0..HIDDEN) for the side to move, [HIDDEN..2*HIDDEN) for the opponent. */
  final short[] outputWeights;

  final int outputBias;

  public Network(
      short[] featureWeights, short[] featureBias, short[] outputWeights, int outputBias) {
    if (featureWeights.length != Features.COUNT * HIDDEN
        || featureBias.length != HIDDEN
        || outputWeights.length != 2 * HIDDEN) {
      throw new IllegalArgumentException("Network arrays have the wrong size");
    }
    this.featureWeights = java.util.Arrays.copyOf(featureWeights, (Features.COUNT + 1) * HIDDEN);
    this.featureBias = featureBias;
    this.outputWeights = outputWeights;
    this.outputBias = outputBias;
  }

  /** A network with small random weights. Only for tests (it plays nonsense chess). */
  public static Network random(long seed) {
    Random r = new Random(seed);
    short[] fw = new short[Features.COUNT * HIDDEN];
    for (int i = 0; i < fw.length; i++) {
      fw[i] = (short) (r.nextInt(41) - 20);
    }
    short[] fb = new short[HIDDEN];
    for (int i = 0; i < HIDDEN; i++) {
      fb[i] = (short) r.nextInt(QA);
    }
    short[] ow = new short[2 * HIDDEN];
    for (int i = 0; i < ow.length; i++) {
      ow[i] = (short) (r.nextInt(81) - 40);
    }
    return new Network(fw, fb, ow, 0);
  }

  /**
   * Evaluation in centipawns for the side whose hidden layer is {@code us}, against {@code them}.
   */
  public int evaluate(short[] us, short[] them) {
    long sum = (long) screluDot(us, 0) + screluDot(them, HIDDEN);
    long output = sum / QA + outputBias; // now = float output * QA * QB
    return (int) (output * SCALE / ((long) QA * QB));
  }

  /**
   * Sum of clamp(x, 0, QA)² × weight over one perspective.
   *
   * <p>Written for speed: branch-free clamps ({@code Math.min/max}) and plain {@code int} maths, so
   * the JIT compiles it to SIMD instructions (many lanes per CPU instruction). Computing {@code (c
   * * w) * c} keeps every step inside an int: |c·w| ≤ 255·127 and the 256-term sum stays below 2³¹
   * because training clips weights to ±1.98 (|w| ≤ 127 after quantisation).
   */
  private int screluDot(short[] acc, int weightOffset) {
    if (Simd.ENABLED) {
      return VectorKernels.screluDot(acc, outputWeights, weightOffset);
    }
    return scalarScreluDot(acc, weightOffset);
  }

  /** The plain-Java version of {@link #screluDot} (also used to test the SIMD version). */
  int scalarScreluDot(short[] acc, int weightOffset) {
    int sum = 0;
    for (int i = 0; i < HIDDEN; i++) {
      int c = Math.min(Math.max(acc[i], 0), QA);
      sum += c * outputWeights[weightOffset + i] * c;
    }
    return sum;
  }

  /**
   * Evaluation straight from feature lists (used by tests to compare with the Python trainer; the
   * engine itself uses an {@link Accumulator}). Feature 768 means "unused slot".
   */
  public int evaluateFeatures(int[] usFeatures, int[] themFeatures) {
    return evaluate(sum(usFeatures), sum(themFeatures));
  }

  private short[] sum(int[] features) {
    short[] acc = featureBias.clone();
    for (int f : features) {
      if (f >= 0 && f < Features.COUNT) {
        for (int i = 0; i < HIDDEN; i++) {
          acc[i] += featureWeights[f * HIDDEN + i];
        }
      }
    }
    return acc;
  }
}
