package gui;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import phase1.Colour;
import phase1.FenException;
import phase1.Move;
import phase1.Square;
import phase2.MoveGenerator;
import phase2.MoveList;
import phase2.Position;
import phase3.Game;
import phase3.GameResult;
import phase3.SearchContext;
import phase3.SearchLimits;
import phase3.Searcher;
import uci.Engines;

/**
 * A graphical chess board in your web browser, served by a tiny web server inside the engine jar
 * ({@code engine gui} → http://localhost:8080). It only listens on your own machine.
 *
 * <p>The page ({@code gui/index.html}) draws the board and sends your clicks here; this class keeps
 * the {@link Game}, checks legality with our move generator, and asks the selected engine for its
 * moves. It is a developer tool for watching the engine; the real website is Phase 9.
 *
 * <pre>
 *   GET  /                 the page
 *   GET  /api/state        position, legal moves, move list, result (JSON)
 *   POST /api/move?uci=e2e4        play your move
 *   POST /api/engine-move          let the engine play the side to move
 *   POST /api/new?fen=...          new game (fen optional)
 *   POST /api/undo                 take back one move
 *   POST /api/settings?engine=random&amp;thinkMs=1000
 *   GET  /api/pgn          the game as PGN
 * </pre>
 */
public final class GuiServer {

  private Game game = new Game();
  private String engineName = Engines.defaultName();
  private Searcher searcher = Engines.create(engineName);
  private long thinkMs = 1000;
  private String lastInfo = "";

  // Engine vs Engine: one searcher per engine name, so each side keeps its own hash table.
  private final Map<String, Searcher> watchEngines = new HashMap<>();

  // PGN replay: the loaded games and where we are in the selected one.
  private List<PgnReader.PgnGame> replayGames = List.of();
  private int replayIndex = -1;
  private int replayPly = 0;

  private GuiServer() {}

  /** Starts the server and (if possible) opens the browser. Runs until the program is closed. */
  public static void start(int port, boolean openBrowser) throws IOException {
    GuiServer gui = new GuiServer();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
    server.createContext("/", gui::handle);
    server.start(); // single-threaded: requests are handled one at a time
    String url = "http://localhost:" + port;
    System.out.println("Board running at " + url + "  (press Ctrl+C here to stop)");
    if (!openBrowser) {
      return;
    }
    try {
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI.create(url));
      }
    } catch (Exception e) {
      System.out.println("Open " + url + " in your browser.");
    }
  }

  private void handle(HttpExchange exchange) throws IOException {
    try {
      String path = exchange.getRequestURI().getPath();
      Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
      switch (path) {
        case "/", "/index.html" -> sendPage(exchange);
        case "/api/state" -> sendJson(exchange, 200, stateJson(null));
        case "/api/move" -> {
          String uci = query.getOrDefault("uci", "");
          boolean ok = !game.result().isOver() && game.play(uci);
          if (ok) {
            replayIndex = -1;
          }
          sendJson(exchange, ok ? 200 : 400, stateJson(ok ? null : "Illegal move: " + uci));
        }
        case "/api/engine-move" -> {
          String error = engineMove(query.get("engine"));
          sendJson(exchange, 200, stateJson(error));
        }
        case "/api/pgn-load" -> {
          String text =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          replayGames = PgnReader.read(text);
          replayIndex = -1;
          sendJson(exchange, 200, gamesJson());
        }
        case "/api/replay" -> {
          int index = Integer.parseInt(query.getOrDefault("game", "0"));
          if (index < 0 || index >= replayGames.size()) {
            sendJson(exchange, 400, stateJson("No such game"));
            return;
          }
          PgnReader.PgnGame g = replayGames.get(index);
          replayIndex = index;
          replayPly =
              Math.max(
                  0, Math.min(g.moves().size(), Integer.parseInt(query.getOrDefault("ply", "0"))));
          game = new Game(g.startFen());
          for (int i = 0; i < replayPly; i++) {
            game.play(g.moves().get(i));
          }
          sendJson(exchange, 200, stateJson(null));
        }
        case "/api/new" -> {
          String fen = query.get("fen");
          String error = null;
          try {
            game = fen == null || fen.isBlank() ? new Game() : new Game(fen);
            searcher.newGame();
            watchEngines.values().forEach(Searcher::newGame);
            replayIndex = -1;
            lastInfo = "";
          } catch (FenException e) {
            error = e.getMessage();
          }
          sendJson(exchange, error == null ? 200 : 400, stateJson(error));
        }
        case "/api/undo" -> {
          game.undo();
          sendJson(exchange, 200, stateJson(null));
        }
        case "/api/settings" -> {
          if (query.containsKey("engine") && Engines.exists(query.get("engine"))) {
            engineName = query.get("engine");
            searcher = Engines.create(engineName);
          }
          if (query.containsKey("thinkMs")) {
            thinkMs = Math.max(10, Long.parseLong(query.get("thinkMs")));
          }
          sendJson(exchange, 200, stateJson(null));
        }
        case "/api/pgn" -> {
          String white = query.getOrDefault("white", "?");
          String black = query.getOrDefault("black", "?");
          send(exchange, 200, "text/plain", game.toPgn(Map.of("White", white, "Black", black)));
        }
        default -> send(exchange, 404, "text/plain", "Not found");
      }
    } catch (RuntimeException e) {
      send(exchange, 500, "text/plain", "Error: " + e);
    }
  }

  /**
   * Lets an engine play the side to move: the selected one, or (Engine vs Engine mode) the engine
   * named in the request.
   */
  private String engineMove(String name) {
    if (game.result().isOver()) {
      return "The game is over";
    }
    Searcher engine = searcher;
    if (name != null && Engines.exists(name)) {
      engine = watchEngines.computeIfAbsent(name, Engines::create);
    }
    Position position = game.copyOfPosition();
    SearchContext context = new SearchContext(line -> lastInfo = line);
    int move = engine.bestMove(position, SearchLimits.moveTime(thinkMs), context);
    if (move == Move.NULL) {
      return "Engine found no move";
    }
    game.play(move);
    replayIndex = -1; // a new move leaves PGN replay mode
    return null;
  }

  // ---------------------------------------------------------------- JSON

  private String stateJson(String error) {
    Position pos = game.position();
    MoveList legal = new MoveList();
    MoveGenerator.generate(pos, legal);
    GameResult result = game.result();
    List<Integer> moves = game.moves();
    int lastMove = moves.isEmpty() ? Move.NULL : moves.get(moves.size() - 1);

    StringBuilder sb = new StringBuilder("{");
    field(sb, "fen", pos.toFen());
    field(sb, "turn", pos.sideToMove() == Colour.WHITE ? "w" : "b");
    sb.append("\"legal\":[");
    for (int i = 0; i < legal.size(); i++) {
      sb.append(i > 0 ? "," : "").append('"').append(Move.toUci(legal.get(i))).append('"');
    }
    sb.append("],");
    sb.append("\"san\":[");
    List<String> san = game.sanMoves();
    for (int i = 0; i < san.size(); i++) {
      sb.append(i > 0 ? "," : "").append('"').append(san.get(i)).append('"');
    }
    sb.append("],");
    field(sb, "lastMove", lastMove == Move.NULL ? "" : Move.toUci(lastMove));
    field(sb, "check", pos.inCheck() ? Square.name(pos.board().kingSquare(pos.sideToMove())) : "");
    field(sb, "result", result.pgn);
    field(sb, "status", result.isOver() ? result.description : "");
    field(sb, "engine", engineName);
    sb.append("\"engines\":[");
    boolean first = true;
    for (String name : Engines.names()) {
      sb.append(first ? "" : ",").append('"').append(name).append('"');
      first = false;
    }
    sb.append("],");
    sb.append("\"thinkMs\":").append(thinkMs).append(',');
    if (replayIndex >= 0) {
      PgnReader.PgnGame g = replayGames.get(replayIndex);
      sb.append("\"replay\":{\"game\":")
          .append(replayIndex)
          .append(",\"ply\":")
          .append(replayPly)
          .append(",\"total\":")
          .append(g.moves().size())
          .append(",\"title\":\"")
          .append(escape(g.title()))
          .append("\"},");
    }
    field(sb, "info", lastInfo);
    field(sb, "error", error == null ? "" : error);
    sb.setLength(sb.length() - 1);
    return sb.append('}').toString();
  }

  private String gamesJson() {
    StringBuilder sb = new StringBuilder("{\"games\":[");
    for (int i = 0; i < replayGames.size(); i++) {
      PgnReader.PgnGame g = replayGames.get(i);
      sb.append(i > 0 ? "," : "")
          .append("{\"title\":\"")
          .append(escape(g.title()))
          .append("\",\"plies\":")
          .append(g.moves().size())
          .append('}');
    }
    return sb.append("]}").toString();
  }

  private static void field(StringBuilder sb, String name, String value) {
    sb.append('"').append(name).append("\":\"").append(escape(value)).append("\",");
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }

  // ---------------------------------------------------------------- HTTP helpers

  private static Map<String, String> parseQuery(String raw) {
    Map<String, String> map = new HashMap<>();
    if (raw == null || raw.isEmpty()) {
      return map;
    }
    for (String pair : raw.split("&")) {
      int eq = pair.indexOf('=');
      String key = eq < 0 ? pair : pair.substring(0, eq);
      String value = eq < 0 ? "" : pair.substring(eq + 1);
      map.put(
          URLDecoder.decode(key, StandardCharsets.UTF_8),
          URLDecoder.decode(value, StandardCharsets.UTF_8));
    }
    return map;
  }

  private static void sendPage(HttpExchange exchange) throws IOException {
    try (InputStream in = GuiServer.class.getResourceAsStream("/gui/index.html")) {
      if (in == null) {
        send(exchange, 500, "text/plain", "gui/index.html missing from the jar");
        return;
      }
      send(exchange, 200, "text/html", new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
    send(exchange, status, "application/json", json);
  }

  private static void send(HttpExchange exchange, int status, String type, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }
}
