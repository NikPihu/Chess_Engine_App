package nnue;

/**
 * Decides whether the NNUE maths runs on SIMD instructions ({@link VectorKernels}, many numbers per
 * CPU instruction) or on plain Java loops.
 *
 * <p>SIMD uses Java's Vector API, which in Java 21 is still an "incubator" module: it is only
 * available when the JVM is started with {@code --add-modules jdk.incubator.vector} (all our
 * launchers do this). Without the flag everything still works, just slower, and both paths give
 * bit-for-bit identical results (tested in {@code NnueTest}).
 */
public final class Simd {

  /** True when the Vector API module is loaded (and not disabled with -Dnnue.simd=false). */
  public static final boolean ENABLED =
      ModuleLayer.boot().findModule("jdk.incubator.vector").isPresent()
          && !"false".equals(System.getProperty("nnue.simd"));

  private Simd() {}

  /** Human-readable status for the engine's startup info. */
  public static String describe() {
    return ENABLED
        ? "SIMD on (" + VectorKernels.lanes() + " int lanes)"
        : "SIMD off (start java with --add-modules jdk.incubator.vector)";
  }
}
