package phase1;

/** Side colours as ints so they can index arrays: {@code colourBitboards[Colour.WHITE]}. */
public final class Colour {

  public static final int WHITE = 0;
  public static final int BLACK = 1;

  private Colour() {}

  /** WHITE ↔ BLACK. XOR with 1 flips the lowest bit, so no branch is needed. */
  public static int opposite(int colour) {
    return colour ^ 1;
  }

  public static String name(int colour) {
    return colour == WHITE ? "white" : "black";
  }
}
