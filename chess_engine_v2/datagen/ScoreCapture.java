package datagen;

import java.util.function.Consumer;
import phase4.Search;

/**
 * Listens to a search's {@code info} lines and keeps the score of the last finished depth, so
 * self-play can label positions with the engine's own judgement.
 */
public final class ScoreCapture implements Consumer<String> {

  /** Score used for "mate in N" (beyond any normal evaluation, below the int16 limit). */
  public static final int MATE_SCORE = 20_000;

  private int score;
  private boolean mate;

  @Override
  public void accept(String line) {
    int i = line.indexOf(" score ");
    if (i < 0) {
      return;
    }
    String[] t = line.substring(i + 7).split(" ");
    if (t[0].equals("cp")) {
      score = Integer.parseInt(t[1]);
      mate = false;
    } else if (t[0].equals("mate")) {
      int moves = Integer.parseInt(t[1]);
      score = moves > 0 ? MATE_SCORE : -MATE_SCORE;
      mate = true;
    }
  }

  public void reset() {
    score = 0;
    mate = false;
  }

  /** Score from the side to move's point of view. */
  public int score() {
    return score;
  }

  public boolean isMate() {
    return mate || Math.abs(score) >= Search.MATE_THRESHOLD;
  }
}
