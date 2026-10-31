package io.github.nikpihu.chess.uci;

import io.github.nikpihu.chess.engine.EngineInfo;
import java.io.PrintStream;

/**
 * Reads one UCI command at a time and writes the replies. Holds the engine state between commands.
 *
 * <p>Protocol reference: https://backscattering.de/chess/uci/
 */
public final class UciSession {

  private final PrintStream out;

  public UciSession(PrintStream out) {
    this.out = out;
  }

  /**
   * Handles one command line.
   *
   * @return {@code false} when the session should end ({@code quit}), otherwise {@code true}
   */
  public boolean handle(String line) {
    String trimmed = line.trim();
    if (trimmed.isEmpty()) {
      return true;
    }
    String[] tokens = trimmed.split("\\s+");
    switch (tokens[0]) {
      case "uci" -> {
        out.println("id name " + EngineInfo.fullName());
        out.println("id author " + EngineInfo.AUTHOR);
        out.println("uciok");
      }
      case "isready" -> out.println("readyok");
      case "quit" -> {
        return false;
      }
      default -> out.println("info string unknown command: " + trimmed);
    }
    return true;
  }
}
