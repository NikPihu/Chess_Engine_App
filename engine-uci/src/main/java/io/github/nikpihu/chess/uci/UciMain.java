package io.github.nikpihu.chess.uci;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Entry point of {@code engine-uci.jar}.
 *
 * <ul>
 *   <li>No arguments: interactive mode, one command per line from stdin (what chess GUIs use).
 *   <li>With arguments: each argument is run as one command, then the program exits. Handy for
 *       quick checks, e.g. {@code java -jar engine-uci.jar "position startpos" d}
 * </ul>
 */
public final class UciMain {

  private UciMain() {}

  public static void main(String[] args) throws IOException {
    PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    UciSession session = new UciSession(out);

    if (args.length > 0) {
      for (String command : args) {
        if (!session.handle(command)) {
          break;
        }
      }
      return;
    }

    BufferedReader in =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    String line;
    while ((line = in.readLine()) != null) {
      if (!session.handle(line)) {
        break;
      }
    }
  }
}
