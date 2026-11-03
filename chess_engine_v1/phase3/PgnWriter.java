package phase3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import phase1.Colour;
import phase1.FenParser;
import phase2.Position;

/**
 * Writes a game as PGN, the standard file format every chess program can open.
 *
 * <pre>
 * [Event "Casual game"]
 * [White "You"]
 * [Black "ChessEngineApp"]
 * [Result "0-1"]
 *
 * 1. f3 e5 2. g4 Qh4# 0-1
 * </pre>
 */
public final class PgnWriter {

  private PgnWriter() {}

  /**
   * @param startFen the position the game started from
   * @param moves the moves played, in order (our 16-bit encoding)
   * @param tags extra PGN tags (Event, White, Black, ...); Result is filled in automatically
   */
  public static String write(String startFen, List<Integer> moves, Map<String, String> tags) {
    Position position = Position.fromFen(startFen);
    StringBuilder movetext = new StringBuilder();
    int moveNumber = position.board().fullmoveNumber();
    boolean first = true;
    for (int move : moves) {
      if (position.sideToMove() == Colour.WHITE) {
        movetext.append(moveNumber).append(". ");
      } else if (first) {
        movetext.append(moveNumber).append("... ");
      }
      movetext.append(San.toSan(position, move)).append(' ');
      if (position.sideToMove() == Colour.BLACK) {
        moveNumber++;
      }
      position.makeMove(move);
      first = false;
    }
    String result = GameRules.result(position).pgn;
    movetext.append(result);

    Map<String, String> all = new LinkedHashMap<>();
    all.put("Event", "Casual game");
    all.put("Site", "ChessEngineApp");
    all.put("White", "?");
    all.put("Black", "?");
    all.putAll(tags);
    all.put("Result", result);
    if (!startFen.equals(FenParser.START_POSITION)) {
      all.put("SetUp", "1");
      all.put("FEN", startFen);
    }

    StringBuilder sb = new StringBuilder();
    all.forEach((k, v) -> sb.append('[').append(k).append(" \"").append(v).append("\"]\n"));
    sb.append('\n').append(wrap(movetext.toString(), 80)).append('\n');
    return sb.toString();
  }

  private static String wrap(String text, int width) {
    StringBuilder sb = new StringBuilder();
    int lineLength = 0;
    for (String word : text.split(" ")) {
      if (lineLength > 0 && lineLength + 1 + word.length() > width) {
        sb.append('\n');
        lineLength = 0;
      } else if (lineLength > 0) {
        sb.append(' ');
        lineLength++;
      }
      sb.append(word);
      lineLength += word.length();
    }
    return sb.toString();
  }
}
