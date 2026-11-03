package datagen;

/**
 * Ends self-play games early when the outcome is already clear, so CPU time goes into informative
 * positions instead of playing out won endings move by move.
 *
 * <ul>
 *   <li><b>Win:</b> one side is ahead by at least {@link #WIN_SCORE} for {@link #WIN_PLIES} plies
 *       in a row (or a forced mate was found).
 *   <li><b>Draw:</b> after move 80, the score stays within ±{@link #DRAW_SCORE} for {@link
 *       #DRAW_PLIES} plies in a row.
 * </ul>
 *
 * Scores are given from white's point of view.
 */
public final class Adjudicator {

  static final int WIN_SCORE = 1000;
  static final int WIN_PLIES = 4;
  static final int DRAW_SCORE = 15;
  static final int DRAW_PLIES = 12;
  static final int DRAW_FROM_PLY = 160;

  /** Result codes, white's point of view (same as {@link Record}). */
  public static final int NONE = -1;

  public static final int BLACK_WINS = 0;
  public static final int DRAW = 1;
  public static final int WHITE_WINS = 2;

  private int whiteWinning;
  private int blackWinning;
  private int quiet;

  /**
   * @return a result code, or {@link #NONE} to keep playing
   */
  public int update(int whiteScore, boolean mate, int ply) {
    if (mate) {
      return whiteScore > 0 ? WHITE_WINS : BLACK_WINS;
    }
    whiteWinning = whiteScore >= WIN_SCORE ? whiteWinning + 1 : 0;
    blackWinning = whiteScore <= -WIN_SCORE ? blackWinning + 1 : 0;
    quiet = Math.abs(whiteScore) <= DRAW_SCORE ? quiet + 1 : 0;
    if (whiteWinning >= WIN_PLIES) {
      return WHITE_WINS;
    }
    if (blackWinning >= WIN_PLIES) {
      return BLACK_WINS;
    }
    if (ply >= DRAW_FROM_PLY && quiet >= DRAW_PLIES) {
      return DRAW;
    }
    return NONE;
  }
}
