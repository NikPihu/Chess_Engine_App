package uci;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
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
import phase3.Game;
import phase3.SearchContext;
import phase3.SearchLimits;
import phase3.Searcher;

/**
 * Reads one UCI command at a time and writes the replies. Holds the current game between commands.
 *
 * <p>Standard UCI: {@code uci}, {@code isready}, {@code ucinewgame}, {@code setoption}, {@code
 * position}, {@code go}, {@code stop}, {@code quit}. Debug commands (not part of UCI, but common in
 * engines): {@code d}, {@code fen}, {@code bb}, {@code moves}, {@code pgn}, {@code go perft N},
 * {@code bench}, {@code help}.
 *
 * <p><b>Threads:</b> {@code go} starts the search on a background thread so this thread can still
 * read {@code stop} and {@code isready} while the engine thinks. When the search finishes it prints
 * {@code bestmove ...}.
 *
 * <p>Protocol reference: https://backscattering.de/chess/uci/
 */
public final class UciSession {

  private final PrintStream out;
  private Game game = new Game();
  private String engineName = Engines.DEFAULT;
  private Searcher searcher = Engines.create(engineName);
  private int hashMb = 16;
  private int moveOverheadMs = 30;

  private Thread searchThread;
  private SearchContext searchContext;

  public UciSession(PrintStream out) {
    this.out = out;
  }

  /** The current board (read-only use, e.g. by tests). */
  public Board board() {
    return game.position().board();
  }

  public Position position() {
    return game.position();
  }

  public String engineName() {
    return engineName;
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
      case "uci" -> uci();
      case "isready" -> out.println("readyok");
      case "ucinewgame" -> {
        waitForSearch();
        game = new Game();
        searcher.newGame();
      }
      case "setoption" -> setOption(tokens);
      case "position" -> position(tokens);
      case "go" -> go(tokens);
      case "stop" -> stopSearch();
      case "d" -> out.print(BoardPrinter.print(board()));
      case "fen" -> out.println(FenWriter.write(board()));
      case "moves" -> listMoves();
      case "pgn" -> out.print(game.toPgn(Map.of("White", "?", "Black", "?")));
      case "bench" -> out.print(Bench.run());
      case "bb" -> bitboard(tokens);
      case "help" -> help();
      case "quit" -> {
        stopSearch();
        return false;
      }
      default -> out.println("info string unknown command: " + trimmed + " (try 'help')");
    }
    return true;
  }

  /** Blocks until a running search has printed its bestmove. */
  public void waitForSearch() {
    Thread thread = searchThread;
    if (thread != null) {
      try {
        thread.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void uci() {
    out.println("id name " + EngineInfo.fullName());
    out.println("id author " + EngineInfo.AUTHOR);
    StringBuilder combo = new StringBuilder();
    for (String name : Engines.names()) {
      combo.append(" var ").append(name);
    }
    out.println("option name Engine type combo default " + Engines.DEFAULT + combo);
    out.println("option name Hash type spin default 16 min 1 max 1024");
    out.println("option name Features type string default all");
    out.println("option name Move Overhead type spin default 30 min 0 max 5000");
    out.println("uciok");
  }

  /** {@code setoption name <Name> value <Value>}. */
  private void setOption(String[] tokens) {
    int nameAt = Arrays.asList(tokens).indexOf("name");
    int valueAt = Arrays.asList(tokens).indexOf("value");
    if (nameAt < 0 || valueAt < 0 || valueAt < nameAt) {
      out.println("info string usage: setoption name <name> value <value>");
      return;
    }
    String name = String.join(" ", Arrays.copyOfRange(tokens, nameAt + 1, valueAt));
    String value = String.join(" ", Arrays.copyOfRange(tokens, valueAt + 1, tokens.length));
    waitForSearch();
    switch (name.toLowerCase()) {
      case "engine" -> {
        if (!Engines.exists(value)) {
          out.println(
              "info string unknown engine '" + value + "', choose one of " + Engines.names());
          return;
        }
        engineName = value;
        searcher = Engines.create(value);
        searcher.setHashSizeMb(hashMb);
      }
      case "hash" -> {
        hashMb = Integer.parseInt(value);
        searcher.setHashSizeMb(hashMb);
      }
      case "move overhead" -> moveOverheadMs = Integer.parseInt(value);
      case "features" -> {
        // Phase 6 switches, e.g. "all", "none", "pvs,nmp" or "all,-nmp" (see phase6/Features).
        if (searcher instanceof phase6.Search2 v2) {
          try {
            v2.setFeatures(phase6.Features.parse(value));
          } catch (IllegalArgumentException e) {
            out.println("info string " + e.getMessage());
          }
        } else {
          out.println("info string Features only applies to engine v2");
        }
      }
      default -> out.println("info string unknown option '" + name + "'");
    }
  }

  /** {@code position startpos|fen <fen> [moves m1 m2 ...]}. */
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

    Game next;
    try {
      next = new Game(fen);
    } catch (FenException e) {
      out.println("info string error: " + e.getMessage());
      return;
    }
    for (int i = end + 1; i < tokens.length; i++) {
      if (!next.play(tokens[i])) {
        out.println(
            "info string error: illegal move '" + tokens[i] + "' in " + next.position().toFen());
        return;
      }
    }
    waitForSearch();
    game = next;
  }

  /** {@code go perft N}, or a real search: {@code go depth 6}, {@code go movetime 1000}, ... */
  private void go(String[] tokens) {
    if (tokens.length >= 2 && tokens[1].equals("perft")) {
      int depth = tokens.length >= 3 ? Integer.parseInt(tokens[2]) : 1;
      long start = System.nanoTime();
      out.print(new Perft(game.copyOfPosition()).divide(depth));
      out.printf("Time: %.2f s%n", (System.nanoTime() - start) / 1e9);
      return;
    }
    waitForSearch();
    SearchLimits limits = SearchLimits.parse(tokens, 1);
    applyMoveOverhead(limits);
    Position searchPosition = game.copyOfPosition();
    Searcher engine = searcher;
    SearchContext context = new SearchContext(out::println);
    searchContext = context;
    searchThread =
        new Thread(
            () -> {
              int best = engine.bestMove(searchPosition, limits, context);
              out.println("bestmove " + Move.toUci(best));
            },
            "search");
    searchThread.start();
  }

  /**
   * Keeps {@code Move Overhead} ms of every clock in reserve for network and GUI delays, so online
   * games (lichess) are not lost on time.
   */
  private void applyMoveOverhead(SearchLimits limits) {
    if (limits.whiteTimeMs > 0) {
      limits.whiteTimeMs = Math.max(1, limits.whiteTimeMs - moveOverheadMs);
    }
    if (limits.blackTimeMs > 0) {
      limits.blackTimeMs = Math.max(1, limits.blackTimeMs - moveOverheadMs);
    }
    if (limits.moveTimeMs > 0) {
      limits.moveTimeMs = Math.max(1, limits.moveTimeMs - moveOverheadMs);
    }
  }

  private void stopSearch() {
    if (searchContext != null) {
      searchContext.stop();
    }
    waitForSearch();
  }

  /** {@code moves}: every legal move in the current position. */
  private void listMoves() {
    MoveList moves = new MoveList();
    MoveGenerator.generate(game.position(), moves);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < moves.size(); i++) {
      sb.append(Move.toUci(moves.get(i))).append(' ');
    }
    out.println(moves.size() + " legal moves: " + sb.toString().trim());
  }

  /** {@code bb <all|white|black|P|N|B|R|Q|K|p|n|b|r|q|k>}: prints one bitboard as a grid. */
  private void bitboard(String[] tokens) {
    Board board = board();
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
          setoption name Engine value <name>   choose the engine version (see 'uci' for names)
          position startpos           set the start position
          position fen <fen>          set any position from FEN
          position ... moves e2e4 ..  then play these moves
          go depth <n>                think n plies deep, then print bestmove
          go movetime <ms>            think for ms milliseconds
          go wtime <ms> btime <ms> [winc <ms> binc <ms>]   think using a clock
          stop                        stop thinking now
          d                           draw the board + FEN, castling, en passant, Zobrist key
          fen                         print the current FEN only
          moves                       list all legal moves
          pgn                         the game so far as PGN
          go perft <depth>            count move sequences (per first move) - tests move generation
          bench                       move-generation speed test (nodes/second)
          bb <all|white|black|P..k>   show one bitboard as an 8x8 grid (P = white pawns, n = black knights, ...)
          help                        this text
          quit                        exit

        Graphical board: start the program as  engine gui""");
  }
}
