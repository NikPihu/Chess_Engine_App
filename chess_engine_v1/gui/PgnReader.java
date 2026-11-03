package gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import phase1.FenParser;
import phase1.Move;
import phase3.Game;
import phase3.San;

/**
 * Reads PGN files (one or many games), e.g. the thousands of games fastchess saves in
 * tools/results/*.pgn, so the board viewer can replay them.
 *
 * <p>Handles tags ({@code [White "x"]}), a {@code [FEN "..."]} start position, comments ({@code
 * {...}} and {@code ;...}, which fastchess uses for scores and times), variations in parentheses,
 * move numbers, NAGs ({@code $1}) and the result token. A game is cut at the first move that can't
 * be read, rather than rejected.
 */
public final class PgnReader {

  /** One game: its tags and the moves as a playable {@link Game}. */
  public record PgnGame(Map<String, String> tags, String startFen, List<Integer> moves) {
    public String title() {
      return tags.getOrDefault("White", "?")
          + " vs "
          + tags.getOrDefault("Black", "?")
          + "  "
          + tags.getOrDefault("Result", "*");
    }
  }

  private PgnReader() {}

  public static List<PgnGame> read(String text) {
    List<PgnGame> games = new ArrayList<>();
    Map<String, String> tags = new LinkedHashMap<>();
    StringBuilder movetext = new StringBuilder();
    for (String raw : text.replace("\r", "").split("\n")) {
      String line = raw.trim();
      if (line.startsWith("[") && line.endsWith("]")) {
        if (movetext.length() > 0) {
          games.add(parse(tags, movetext.toString()));
          tags = new LinkedHashMap<>();
          movetext.setLength(0);
        }
        int space = line.indexOf(' ');
        if (space > 1) {
          String value = line.substring(space + 1, line.length() - 1).trim();
          if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
          }
          tags.put(line.substring(1, space), value);
        }
      } else if (!line.isEmpty()) {
        movetext.append(line).append(' ');
      }
    }
    if (movetext.length() > 0 || !tags.isEmpty()) {
      games.add(parse(tags, movetext.toString()));
    }
    return games;
  }

  private static PgnGame parse(Map<String, String> tags, String movetext) {
    String fen = tags.getOrDefault("FEN", FenParser.START_POSITION);
    Game game;
    try {
      game = new Game(fen);
    } catch (RuntimeException e) {
      game = new Game();
      fen = FenParser.START_POSITION;
    }
    for (String token : clean(movetext).split("\\s+")) {
      if (token.isEmpty()
          || token.matches("\\d+\\.+")
          || token.startsWith("$")
          || token.equals("1-0")
          || token.equals("0-1")
          || token.equals("1/2-1/2")
          || token.equals("*")) {
        continue;
      }
      String san = token.replaceFirst("^\\d+\\.+", "");
      int move = San.parse(game.position(), san);
      if (move == Move.NULL) {
        break; // unreadable move: keep what we have
      }
      game.play(move);
    }
    return new PgnGame(tags, fen, game.moves());
  }

  /** Removes {comments}, ;comments to end of line, and (variations, possibly nested). */
  private static String clean(String movetext) {
    StringBuilder sb = new StringBuilder();
    int depth = 0;
    boolean inBrace = false;
    for (int i = 0; i < movetext.length(); i++) {
      char c = movetext.charAt(i);
      if (inBrace) {
        inBrace = c != '}';
      } else if (c == '{') {
        inBrace = true;
      } else if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth = Math.max(0, depth - 1);
      } else if (depth == 0) {
        sb.append(c);
      }
    }
    return sb.toString().replaceAll(";[^\\n]*", " ");
  }
}
