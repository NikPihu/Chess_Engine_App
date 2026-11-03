package uci;

import gui.GuiServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Entry point of {@code target/engine.jar} (shortcut: {@code engine.cmd}).
 *
 * <ul>
 *   <li>No arguments: interactive mode, one command per line from stdin (what chess GUIs use).
 *   <li>{@code gui [port] [--no-browser]}: opens the graphical board in your browser.
 *   <li>Other arguments: each one is run as a command, then the program exits. Handy for quick
 *       checks, e.g. {@code engine "position startpos" "go depth 5"}. A search started by {@code
 *       go} finishes before the next argument runs.
 * </ul>
 */
public final class UciMain {

  private UciMain() {}

  public static void main(String[] args) throws IOException {
    if (args.length > 0 && args[0].equals("gui")) {
      boolean openBrowser = !java.util.Arrays.asList(args).contains("--no-browser");
      int port = args.length > 1 && args[1].matches("[0-9]+") ? Integer.parseInt(args[1]) : 8080;
      GuiServer.start(port, openBrowser);
      return;
    }

    PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    UciSession session = new UciSession(out);

    if (args.length > 0) {
      for (String command : args) {
        boolean keepGoing = session.handle(command);
        session.waitForSearch();
        if (!keepGoing) {
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
    session.waitForSearch();
  }
}
