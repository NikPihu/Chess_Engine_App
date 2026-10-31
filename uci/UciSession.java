package uci;

import java.io.PrintStream;
import java.util.Arrays;
import phase1.BitboardPrinter;
import phase1.Board;
import phase1.BoardPrinter;
import phase1.Colour;
import phase1.FenException;
import phase1.FenParser;
import phase1.FenWriter;
import phase1.Piece;

/**
 * Reads one UCI command at a time and writes the replies. Holds the current position between
 * commands.
 *
 * <p>Standard UCI commands: {@code uci}, {@code isready}, {@code ucinewgame}, {@code position},
 * {@code quit}. Debug commands (not part of UCI, but common in engines): {@code d}, {@code bb},
 * {@code fen}, {@code help}.
 *
 * <p>Protocol reference: https://backscattering.de/chess/uci/
 */
public final class UciSession {

  private final PrintStream out;
  private Board board = FenParser.parse(FenParser.START_POSITION);

  public UciSession(PrintStream out) {
    this.out = out;
  }

  /** The current position (read-only use, e.g. by tests). */
  public Board board() {
    return board;
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
      case "ucinewgame" -> board = FenParser.parse(FenParser.START_POSITION);
      case "position" -> position(tokens);
      case "d" -> out.print(BoardPrinter.print(board));
      case "fen" -> out.println(FenWriter.write(board));
      case "bb" -> bitboard(tokens);
      case "help" -> help();
      case "quit" -> {
        return false;
      }
      default -> out.println("info string unknown command: " + trimmed + " (try 'help')");
    }
    return true;
  }

  /** {@code position startpos [moves ...]} or {@code position fen <fen> [moves ...]}. */
  private void position(String[] tokens) {
    if (tokens.length < 2) {
      out.println("info string usage: position startpos | position fen <fen> [moves ...]");
      return;
    }
    int movesIndex = Arrays.asList(tokens).indexOf("moves");
    int end = movesIndex < 0 ? tokens.length : movesIndex;

    String fen;
    if (tokens[1].equals("startpos")) {
      fen = FenParser.START_POSITION;
    } else if (tokens[1].equals("fen")) {
      fen = String.join(" ", Arrays.copyOfRange(tokens, 2, end));
    } else {
      out.println("info string position: expected 'startpos' or 'fen', got '" + tokens[1] + "'");
      return;
    }

    try {
      board = FenParser.parse(fen);
    } catch (FenException e) {
      out.println("info string error: " + e.getMessage());
      return;
    }

    if (movesIndex >= 0 && movesIndex < tokens.length - 1) {
      out.println("info string moves are ignored until Phase 2 (move generation)");
    }
  }

  /** {@code bb <all|white|black|P|N|B|R|Q|K|p|n|b|r|q|k>}: prints one bitboard as a grid. */
  private void bitboard(String[] tokens) {
    if (tokens.length != 2) {
      out.println("info string usage: bb <all|white|black|P|N|B|R|Q|K|p|n|b|r|q|k>");
      return;
    }
    String what = tokens[1];
    long bb;
    switch (what) {
      case "all" -> bb = board.occupied();
      case "white" -> bb = board.piecesOf(Colour.WHITE);
      case "black" -> bb = board.piecesOf(Colour.BLACK);
      default -> {
        int piece = what.length() == 1 ? Piece.fromChar(what.charAt(0)) : Piece.NONE;
        if (piece == Piece.NONE) {
          out.println("info string bb: unknown bitboard '" + what + "'");
          return;
        }
        bb = board.pieces(piece);
      }
    }
    out.print(BitboardPrinter.print(bb));
  }

  private void help() {
    out.println(
        """
        Commands:
          uci                         identify the engine (UCI handshake)
          isready                     replies readyok
          ucinewgame                  reset to the start position
          position startpos           set the start position
          position fen <fen>          set any position from FEN
          d                           draw the board + FEN, castling, en passant, Zobrist key
          fen                         print the current FEN only
          bb <all|white|black|P..k>   show one bitboard as an 8x8 grid (P = white pawns, n = black knights, ...)
          help                        this text
          quit                        exit""");
  }
}
