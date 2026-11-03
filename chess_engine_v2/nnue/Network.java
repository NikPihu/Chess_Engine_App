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

  /** featureWeights[feature * HIDDEN + i]. */
  final short[] featureWeights;

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
    this.featureWeights = featureWeights;
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
    long sum = 0;
    for (int i = 0; i < HIDDEN; i++) {
      sum += screlu(us[i]) * outputWeights[i];
      sum += screlu(them[i]) * outputWeights[HIDDEN + i];
    }
    long output = sum / QA + outputBias; // now = float output * QA * QB
    return (int) (output * SCALE / ((long) QA * QB));
  }

  /** Clamp to [0, QA] then square: the integer version of clamp(x, 0, 1)². */
  private static long screlu(short x) {
    int c = x < 0 ? 0 : Math.min(x, QA);
    return (long) c * c;
  }
}
