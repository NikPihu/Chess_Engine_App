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
import phase1.Move;
import phase1.Piece;
import phase2.Bench;
import phase2.MoveGenerator;
import phase2.MoveList;
import phase2.Perft;
import phase2.Position;
import phase2.UciMoveParser;

/**
 * Reads one UCI command at a time and writes the replies. Holds the current position between
 * commands.
 *
 * <p>Standard UCI commands: {@code uci}, {@code isready}, {@code ucinewgame}, {@code position},
 * {@code quit}. Debug commands (not part of UCI, but common in engines): {@code d}, {@code bb},
 * {@code fen}, {@code moves}, {@code go perft N}, {@code bench}, {@code help}.
 *
 * <p>Protocol reference: https://backscattering.de/chess/uci/
 */
public final class UciSession {

  private final PrintStream out;
  private Position position = Position.startPosition();

  public UciSession(PrintStream out) {
    this.out = out;
  }

  /** The current board (read-only use, e.g. by tests). */
  public Board board() {
    return position.board();
  }

  public Position position() {
    return position;
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
      case "ucinewgame" -> position = Position.startPosition();
      case "position" -> position(tokens);
      case "d" -> out.print(BoardPrinter.print(position.board()));
      case "fen" -> out.println(FenWriter.write(position.board()));
      case "moves" -> listMoves();
      case "go" -> go(tokens);
      case "bench" -> out.print(Bench.run());
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

    Position next;
    try {
      next = new Position(FenParser.parse(fen));
    } catch (FenException e) {
      out.println("info string error: " + e.getMessage());
      return;
    }

    for (int i = end + 1; i < tokens.length; i++) {
      int move = UciMoveParser.parse(next, tokens[i]);
      if (move == Move.NULL) {
        out.println("info string error: illegal move '" + tokens[i] + "' in " + next.toFen());
        return;
      }
      next.makeMove(move);
    }
    position = next;
  }

  /** {@code go perft N}: per-move node counts (searching arrives in Phase 3/4). */
  private void go(String[] tokens) {
    if (tokens.length == 3 && tokens[1].equals("perft")) {
      int depth = Integer.parseInt(tokens[2]);
      long start = System.nanoTime();
      out.print(new Perft(position).divide(depth));
      out.printf("Time: %.2f s%n", (System.nanoTime() - start) / 1e9);
      return;
    }
    out.println("info string usage: go perft <depth>");
  }

  /** {@code moves}: every legal move in the current position. */
  private void listMoves() {
    MoveList moves = new MoveList();
    MoveGenerator.generate(position, moves);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < moves.size(); i++) {
      sb.append(Move.toUci(moves.get(i))).append(' ');
    }
    out.println(moves.size() + " legal moves: " + sb.toString().trim());
  }

  /** {@code bb <all|white|black|P|N|B|R|Q|K|p|n|b|r|q|k>}: prints one bitboard as a grid. */
  private void bitboard(String[] tokens) {
    Board board = position.board();
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
          position ... moves e2e4 ..  then play these moves
          moves                       list all legal moves
          go perft <depth>            count move sequences (per first move) - tests move generation
          bench                       move-generation speed test (nodes/second)
          d                           draw the board + FEN, castling, en passant, Zobrist key
          fen                         print the current FEN only
          bb <all|white|black|P..k>   show one bitboard as an 8x8 grid (P = white pawns, n = black knights, ...)
          help                        this text
          quit                        exit""");
  }
}
