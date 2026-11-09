package com.gambyte.game.service;

import com.gambyte.common.ApiException;
import com.gambyte.common.Tokens;
import com.gambyte.engine.ComputerPlayer;
import com.gambyte.engine.EngineLevel;
import com.gambyte.game.api.GameDtos.ClockView;
import com.gambyte.game.api.GameDtos.GameEvent;
import com.gambyte.game.api.GameDtos.GamePage;
import com.gambyte.game.api.GameDtos.GameSummary;
import com.gambyte.game.api.GameDtos.GameView;
import com.gambyte.game.api.GameDtos.PlayerView;
import com.gambyte.game.domain.GameRecord;
import com.gambyte.game.domain.GameStatus;
import com.gambyte.game.live.ChessClock;
import com.gambyte.game.live.LiveGame;
import com.gambyte.game.live.LiveGameStore;
import com.gambyte.game.repo.GameRepository;
import com.gambyte.rating.RatingService;
import com.gambyte.user.api.UserDtos.UserRef;
import com.gambyte.user.domain.Perf;
import com.gambyte.user.domain.User;
import com.gambyte.user.service.UserService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import phase1.Colour;
import phase1.FenParser;
import phase2.Position;
import phase3.San;

/**
 * The game feature's main service: creates games, applies moves and other actions, runs the
 * computer opponent, watches clocks, and finishes games (save, rate, announce).
 *
 * <p>Flow of one move: {@code GameController}/{@code GameSocketController} → {@link #move} →
 * {@link LiveGame#move} (legality, clock) → {@link LiveGameStore#save} (Redis snapshot, async) →
 * {@link GameEvents#publish} (WebSocket to both players and spectators) → if the opponent is the
 * engine, {@link #scheduleEngineMove} asks the {@link ComputerPlayer} on a virtual thread.
 */
@Service
public class GameService {

  private static final Logger log = LoggerFactory.getLogger(GameService.class);

  private final LiveGameStore live;
  private final GameRepository repo;
  private final UserService users;
  private final RatingService ratings;
  private final ComputerPlayer computer;
  private final GameEvents events;
  private final TransactionTemplate tx;
  private final ExecutorService engineThreads = Executors.newVirtualThreadPerTaskExecutor();

  public GameService(
      LiveGameStore live,
      GameRepository repo,
      UserService users,
      RatingService ratings,
      ComputerPlayer computer,
      GameEvents events,
      TransactionTemplate tx) {
    this.live = live;
    this.repo = repo;
    this.users = users;
    this.ratings = ratings;
    this.computer = computer;
    this.events = events;
    this.tx = tx;
  }

  // ------------------------------------------------------------------ creating games

  /** A casual game against Gambyte Engine at a level, with or without a clock. */
  public GameView createComputerGame(
      long userId, int level, String color, int initialSec, int incrementSec, String fen) {
    boolean busy =
        live.all().stream()
            .filter(g -> g.colourOf(userId) >= 0 && "computer".equals(g.source()))
            .anyMatch(g -> !g.status().isOver());
    if (busy) {
      // Only one computer game at a time per player keeps the engine pool fair to everyone.
      live.all().stream()
          .filter(g -> g.colourOf(userId) >= 0 && "computer".equals(g.source()))
          .forEach(g -> resignQuietly(g, userId));
    }
    String startFen = (fen == null || fen.isBlank()) ? FenParser.START_POSITION : fen.trim();
    try {
      Position.fromFen(startFen);
    } catch (RuntimeException e) {
      throw ApiException.badRequest("bad_fen", "That FEN is not a valid position");
    }
    boolean userWhite =
        switch (color == null ? "random" : color) {
          case "white" -> true;
          case "black" -> false;
          default -> ThreadLocalRandom.current().nextBoolean();
        };
    UserRef engine = users.engineUser();
    long whiteId = userWhite ? userId : engine.id();
    long blackId = userWhite ? engine.id() : userId;
    LiveGame game =
        start(whiteId, blackId, initialSec, incrementSec, false, "computer", null, startFen, level);
    return view(game);
  }

  /**
   * Starts a game between two accounts (matchmaking, challenges, tournaments, bots).
   *
   * @param engineLevel set when one side is Gambyte Engine; null otherwise
   */
  public LiveGame start(
      long whiteId,
      long blackId,
      int initialSec,
      int incrementSec,
      boolean rated,
      String source,
      Long tournamentId,
      String startFen,
      Integer engineLevel) {
    Perf perf = Perf.forTimeControl(initialSec, incrementSec);
    Map<Long, User> both = users.getAll(List.of(whiteId, blackId));
    LiveGame.Player white = player(both.get(whiteId), perf, engineLevel);
    LiveGame.Player black = player(both.get(blackId), perf, engineLevel);
    String id = Tokens.shortId(8);
    long now = System.currentTimeMillis();
    LiveGame game =
        new LiveGame(
            id, white, black, perf.key(), rated, source, tournamentId, startFen,
            initialSec * 1000L, incrementSec * 1000L, now);

    GameRecord record =
        new GameRecord(id, whiteId, blackId, perf.key(), initialSec, incrementSec, rated, source,
            game.startFen());
    record.setRatings(white.rating(), black.rating());
    record.setEngineLevel(engineLevel);
    record.setTournamentId(tournamentId);
    repo.save(record);

    live.put(game);
    events.started(new GameEvents.GameStarted(id, whiteId, blackId));
    log.debug("Game {} started: {} vs {} ({})", id, white.username(), black.username(), perf.key());
    scheduleEngineMove(game);
    return game;
  }

  private LiveGame.Player player(User u, Perf perf, Integer engineLevel) {
    boolean isEngine = User.ENGINE_USERNAME.equals(u.getUsername());
    if (isEngine) {
      int level = engineLevel == null ? 8 : engineLevel;
      return new LiveGame.Player(u.getId(), u.getUsername(), u.getTitle(),
          EngineLevel.of(level).approxRating, false, level);
    }
    var r = users.ratingFor(u.getId(), perf);
    return new LiveGame.Player(u.getId(), u.getUsername(), u.getTitle(),
        (int) Math.round(r.getRating()), r.isProvisional(), null);
  }

  // ------------------------------------------------------------------ player actions

  /** Plays a move for a user. Returns the new state version. */
  public int move(String gameId, long userId, String uci, long lagMs) {
    LiveGame game = running(gameId);
    int colour = colourOrThrow(game, userId);
    LiveGame.MoveResult r = game.move(colour, uci, System.currentTimeMillis(), lagMs);
    afterMove(game, r);
    return r.version();
  }

  private void afterMove(LiveGame game, LiveGame.MoveResult r) {
    events.publish(game.id(), GameEvent.move(r.version(), r.uci(), r.san(), r.fen(), r.ply(), clock(game)));
    if (game.status().isOver()) {
      finish(game);
    } else {
      live.save(game);
      scheduleEngineMove(game);
    }
  }

  public void resign(String gameId, long userId) {
    LiveGame game = running(gameId);
    game.resign(colourOrThrow(game, userId));
    finish(game);
  }

  public void abort(String gameId, long userId) {
    LiveGame game = running(gameId);
    game.abort(colourOrThrow(game, userId));
    finish(game);
  }

  public void offerDraw(String gameId, long userId) {
    LiveGame game = running(gameId);
    int colour = colourOrThrow(game, userId);
    LiveGame.Player opponent = game.player(Colour.opposite(colour));
    if (opponent.isEngine()) {
      // The engine accepts a draw only when it thinks the position is level or worse for it.
      throw ApiException.badRequest("engine_declines", "Gambyte Engine declines the draw");
    }
    game.offerDraw(colour);
    if (game.status().isOver()) {
      finish(game);
    } else {
      events.publish(gameId, GameEvent.simple("draw", game.version(), Colour.name(colour)));
      live.save(game);
    }
  }

  public void declineDraw(String gameId, long userId) {
    LiveGame game = running(gameId);
    game.declineDraw(colourOrThrow(game, userId));
    events.publish(gameId, GameEvent.simple("draw", game.version(), null));
  }

  /** Takebacks are only allowed in casual games against the computer. */
  public GameView takeback(String gameId, long userId) {
    LiveGame game = running(gameId);
    int colour = colourOrThrow(game, userId);
    if (!game.player(Colour.opposite(colour)).isEngine() || game.rated()) {
      throw ApiException.badRequest("no_takeback", "Takebacks are only allowed against the computer");
    }
    game.takeback(colour);
    live.save(game);
    events.publish(gameId, GameEvent.simple("state", game.version(), null));
    return view(game);
  }

  public void chat(String gameId, long userId, String text) {
    LiveGame game = live.get(gameId).orElseThrow(() -> ApiException.notFound("game"));
    User u = users.get(userId);
    if (u.isMuted()) throw ApiException.forbidden("muted", "You cannot chat right now");
    if (game.colourOf(userId) < 0) throw ApiException.forbidden("not_player", "Only players can chat");
    String clean = ChatFilter.clean(text);
    game.say(u.getUsername(), clean, System.currentTimeMillis());
    events.publish(gameId, GameEvent.chat(u.getUsername(), clean));
  }

  /** The browser tab lost focus during the game (used by fair-play statistics). */
  public void blur(String gameId, long userId) {
    live.get(gameId).ifPresent(g -> {
      int colour = g.colourOf(userId);
      if (colour >= 0) g.recordBlur(colour);
    });
  }

  /**
   * Offers a rematch; when both players have offered, starts the new game (colours swapped) and
   * tells both players its id. Games against the engine restart immediately.
   */
  public String rematch(String gameId, long userId) {
    LiveGame old = live.get(gameId).orElseThrow(
        () -> ApiException.badRequest("rematch_expired", "This game can no longer be rematched"));
    int colour = colourOrThrow(old, userId);
    LiveGame.Player opponent = old.player(Colour.opposite(colour));
    boolean ready = opponent.isEngine() || old.offerRematch(colour);
    if (!ready) {
      events.publish(gameId, GameEvent.simple("rematch", old.version(), Colour.name(colour)));
      return null;
    }
    ChessClock c = old.clock();
    LiveGame next =
        start(old.black().userId(), old.white().userId(), (int) (c.initialMs() / 1000),
            (int) (c.incrementMs() / 1000), old.rated(), old.source(), null, old.startFen(),
            old.white().isEngine() ? old.white().engineLevel() : old.black().engineLevel());
    events.publish(gameId, GameEvent.rematchReady(next.id()));
    live.remove(gameId);
    return next.id();
  }

  // ------------------------------------------------------------------ the computer opponent

  /** If it is the engine's turn, think on a virtual thread and play the move. */
  private void scheduleEngineMove(LiveGame game) {
    if (game.status().isOver()) return;
    LiveGame.Player toMove = game.player(game.sideToMove());
    if (!toMove.isEngine()) return;
    int versionAtStart = game.version();
    engineThreads.submit(() -> {
      try {
        long now = System.currentTimeMillis();
        ChessClock c = game.clock();
        // Before both sides have moved the clocks aren't running; answer quickly instead of
        // spending a slice of a full clock on move one.
        long[] clockMs = c.isUntimed() || game.moves().size() < 2 ? null
            : new long[] {c.remaining(0, now), c.remaining(1, now), c.incrementMs(), c.incrementMs()};
        String uci = computer.chooseMove(game.startFen(), game.moves(),
            EngineLevel.of(toMove.engineLevel()), clockMs);
        // Nothing to do if the game changed meanwhile (takeback, resignation...).
        if (uci == null || game.version() != versionAtStart || game.status().isOver()) return;
        LiveGame.MoveResult r = game.move(game.sideToMove(), uci, System.currentTimeMillis(), 0);
        afterMove(game, r);
      } catch (ApiException e) {
        log.debug("Engine move skipped in {}: {}", game.id(), e.getMessage());
      } catch (RuntimeException e) {
        log.error("Engine failed in game {}", game.id(), e);
      }
    });
  }

  // ------------------------------------------------------------------ clocks

  /**
   * Ten times a second: end games whose flag has fallen or whose first move never came. Scanning
   * all live games is cheap (a few thousand comparisons) and simpler than one timer per game.
   */
  @Scheduled(fixedDelay = 100)
  public void tick() {
    long now = System.currentTimeMillis();
    for (LiveGame game : live.all()) {
      if (game.status().isOver()) continue;
      int flagged = game.clock().flagged(now);
      if (flagged >= 0) {
        game.flag(flagged);
        finish(game);
      } else if (game.checkFirstMoveTimeout(now)) {
        finish(game);
      }
    }
  }

  /** Finished games stay in memory for 5 minutes (for rematch offers), then are dropped. */
  @Scheduled(fixedDelay = 60_000)
  public void evictFinished() {
    long cutoff = System.currentTimeMillis() - 5 * 60_000;
    live.all().stream()
        .filter(g -> g.status().isOver() && g.lastMoveAt() < cutoff)
        .map(LiveGame::id)
        .toList()
        .forEach(id -> {
          live.remove(id);
          finished.remove(id);
        });
  }

  // ------------------------------------------------------------------ ending

  /**
   * Saves a finished game, updates ratings, tells the players and the rest of the server. Safe to
   * call more than once (only the first call does anything).
   */
  public void finish(LiveGame game) {
    if (game.status() == GameStatus.STARTED) return;
    // Two threads can notice the same ending (e.g. the clock tick and a move); only the first one
    // to add the id here does the work.
    if (!finished.add(game.id())) return;
    tx.executeWithoutResult(status -> persistEnd(game));
  }

  /** Ids of games already finished in this server run (cleared when they leave memory). */
  private final java.util.Set<String> finished = java.util.concurrent.ConcurrentHashMap.newKeySet();

  private void persistEnd(LiveGame game) {
    GameRecord record = repo.findById(game.id()).orElseThrow();
    Integer wDiff = null;
    Integer bDiff = null;
    boolean countsForRating = game.rated() && game.status() != GameStatus.ABORTED;
    if (countsForRating) {
      double whiteScore = game.winner() == null ? 0.5 : "white".equals(game.winner()) ? 1 : 0;
      var diffs = ratings.apply(game.white().userId(), game.black().userId(),
          Perf.fromKey(game.perf()), whiteScore);
      wDiff = diffs.white();
      bDiff = diffs.black();
      record.setRatingDiffs(wDiff, bDiff);
    }
    record.finish(game.status(), game.winner(), game.termination(), game.moves(), game.moveTimes(),
        game.fen());
    record.setOpening(Openings.name(game.sans()));
    int[] blurs = game.blurs();
    record.setBlurs(blurs[0], blurs[1]);
    repo.save(record);
    events.publish(game.id(), GameEvent.end(game.version(), game.status().name(), game.winner(),
        game.termination(), clock(game), wDiff, bDiff));
    events.finished(new GameEvents.GameFinished(game.id(), game.white().userId(),
        game.black().userId(), countsForRating, game.winner(), game.tournamentId()));
    live.save(game);
    log.debug("Game {} finished: {} ({})", game.id(), game.status(), game.termination());
  }

  private void resignQuietly(LiveGame g, long userId) {
    try {
      if (!g.status().isOver()) {
        if (g.moves().size() < 2) g.abort(g.colourOf(userId)); else g.resign(g.colourOf(userId));
        finish(g);
      }
    } catch (ApiException ignored) {
      // already over
    }
  }

  // ------------------------------------------------------------------ start-up recovery

  /** Games that were running when the server stopped are restored from their Redis snapshots. */
  @EventListener(ApplicationReadyEvent.class)
  public void restoreRunningGames() {
    for (GameRecord r : repo.findByStatus(GameStatus.STARTED)) {
      var restored = live.restore(r.getId());
      if (restored.isPresent()) {
        log.info("Restored running game {}", r.getId());
        scheduleEngineMove(restored.get());
      } else {
        tx.executeWithoutResult(s -> {
          GameRecord rec = repo.findById(r.getId()).orElseThrow();
          rec.finish(GameStatus.ABORTED, null, "Aborted (server restart)", List.of(), List.of(),
              rec.getStartFen());
        });
      }
    }
  }

  // ------------------------------------------------------------------ reading

  public GameView view(String id) {
    return live.get(id).map(this::view).orElseGet(() -> view(record(id)));
  }

  public GameView view(LiveGame g) {
    GameRecord rec = g.status().isOver() ? repo.findById(g.id()).orElse(null) : null;
    int side = g.sideToMove();
    return new GameView(
        g.id(),
        playerView(g.white(), rec == null ? null : rec.getWhiteRatingDiff()),
        playerView(g.black(), rec == null ? null : rec.getBlackRatingDiff()),
        g.perf(), g.rated(), g.source(), g.status().name(), g.winner(), g.termination(),
        g.startFen(), g.fen(), g.moves(), g.sans(), g.moveTimes(), clock(g),
        Colour.name(side),
        g.drawOfferBy() < 0 ? null : Colour.name(g.drawOfferBy()),
        g.rematchOfferBy() < 0 ? null : Colour.name(g.rematchOfferBy()),
        g.version(), g.tournamentId(), Instant.ofEpochMilli(g.createdAt()),
        rec == null ? null : rec.getEndedAt());
  }

  private GameView view(GameRecord r) {
    Map<Long, User> both = users.getAll(List.of(r.getWhiteId(), r.getBlackId()));
    List<String> moves = r.moveList();
    List<String> sans = sans(r.getStartFen(), moves);
    String fen = r.getFinalFen() == null ? r.getStartFen() : r.getFinalFen();
    String turn = moves.size() % 2 == 0 == r.getStartFen().contains(" w ") ? "white" : "black";
    return new GameView(
        r.getId(),
        playerView(both.get(r.getWhiteId()), r.getWhiteRating(), r.getWhiteRatingDiff(), r.getEngineLevel()),
        playerView(both.get(r.getBlackId()), r.getBlackRating(), r.getBlackRatingDiff(), r.getEngineLevel()),
        r.getPerf(), r.isRated(), r.getSource(), r.getStatus().name(), r.getWinner(),
        r.getTermination(), r.getStartFen(), fen, moves, sans, r.moveTimeList(),
        new ClockView(0, 0, null, r.getClockInitial() * 1000L, r.getClockIncrement() * 1000L,
            System.currentTimeMillis()),
        turn, null, null, 0, r.getTournamentId(), r.getCreatedAt(), r.getEndedAt());
  }

  public GameRecord record(String id) {
    return repo.findById(id).orElseThrow(() -> ApiException.notFound("game"));
  }

  /** A player's finished games, newest first, 20 per page. */
  public GamePage archive(String username, Instant before, int limit) {
    User u = users.getByUsername(username);
    int size = Math.min(Math.max(limit, 1), 50);
    List<GameRecord> rows =
        repo.archive(u.getId(), before == null ? Instant.now() : before, PageRequest.of(0, size));
    List<Long> ids = new ArrayList<>();
    rows.forEach(r -> { ids.add(r.getWhiteId()); ids.add(r.getBlackId()); });
    Map<Long, User> people = users.getAll(ids);
    List<GameSummary> out = rows.stream().map(r -> new GameSummary(
        r.getId(),
        playerView(people.get(r.getWhiteId()), r.getWhiteRating(), r.getWhiteRatingDiff(), r.getEngineLevel()),
        playerView(people.get(r.getBlackId()), r.getBlackRating(), r.getBlackRatingDiff(), r.getEngineLevel()),
        r.getPerf(), r.isRated(), r.getStatus().name(), r.getWinner(), r.getTermination(),
        r.getPly(), r.getFinalFen(), r.getClockInitial(), r.getClockIncrement(), r.getCreatedAt()))
        .toList();
    Instant next = rows.size() == size ? rows.getLast().getCreatedAt() : null;
    return new GamePage(out, next);
  }

  /** All running games (used by the tournament pairer to know who is busy). */
  public List<LiveGame> ongoingAll() {
    return live.all().stream().filter(g -> !g.status().isOver()).toList();
  }

  /** Number of games being played right now. */
  public int runningCount() {
    return (int) live.all().stream().filter(g -> !g.status().isOver()).count();
  }

  /** Running games, best-rated first (for "Gambyte TV" and the watch page). */
  public List<GameView> topLive(int limit) {
    return live.all().stream()
        .filter(g -> !g.status().isOver())
        .sorted(java.util.Comparator.comparingInt((LiveGame g) ->
            -(nz(g.white().rating()) + nz(g.black().rating()))))
        .limit(limit)
        .map(this::view)
        .toList();
  }

  private static int nz(Integer v) {
    return v == null ? 0 : v;
  }

  /** Games this user is playing right now (to resume after closing the tab). */
  public List<GameView> ongoing(long userId) {
    return live.all().stream()
        .filter(g -> g.colourOf(userId) >= 0 && !g.status().isOver())
        .map(this::view)
        .toList();
  }

  /** The finished game as PGN text, with the standard tags. */
  public String pgn(String id) {
    GameRecord r = record(id);
    Map<Long, User> both = users.getAll(List.of(r.getWhiteId(), r.getBlackId()));
    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("Event", (r.isRated() ? "Rated " : "Casual ") + r.getPerf() + " game");
    tags.put("Site", "https://gambyte.app/" + r.getId());
    tags.put("Date", r.getCreatedAt().toString().substring(0, 10).replace('-', '.'));
    tags.put("White", both.get(r.getWhiteId()).getUsername());
    tags.put("Black", both.get(r.getBlackId()).getUsername());
    tags.put("Result", result(r));
    if (r.getWhiteRating() != null) tags.put("WhiteElo", String.valueOf(r.getWhiteRating()));
    if (r.getBlackRating() != null) tags.put("BlackElo", String.valueOf(r.getBlackRating()));
    tags.put("TimeControl", r.getClockInitial() == 0 && r.getClockIncrement() == 0 ? "-"
        : r.getClockInitial() + "+" + r.getClockIncrement());
    if (r.getTermination() != null) tags.put("Termination", r.getTermination());
    if (r.getOpening() != null) tags.put("Opening", r.getOpening());
    if (!FenParser.START_POSITION.equals(r.getStartFen())) {
      tags.put("SetUp", "1");
      tags.put("FEN", r.getStartFen());
    }
    phase3.Game game = new phase3.Game(r.getStartFen());
    r.moveList().forEach(game::play);
    String pgn = game.toPgn(tags);
    // PgnWriter takes the result from the board position, which says "*" for resignations and
    // timeouts; put the real result in the tag and after the moves.
    String result = result(r);
    pgn = pgn.replace("[Result \"*\"]", "[Result \"" + result + "\"]");
    return pgn.endsWith(" *\n") || pgn.endsWith(" *")
        ? pgn.substring(0, pgn.lastIndexOf(" *")) + " " + result + "\n"
        : pgn;
  }

  private static String result(GameRecord r) {
    if (r.getStatus() == GameStatus.STARTED || r.getStatus() == GameStatus.ABORTED) return "*";
    if (r.getWinner() == null) return "1/2-1/2";
    return "white".equals(r.getWinner()) ? "1-0" : "0-1";
  }

  // ------------------------------------------------------------------ helpers

  private LiveGame running(String gameId) {
    LiveGame g = live.get(gameId).orElseThrow(() -> ApiException.notFound("game"));
    if (g.status().isOver()) throw ApiException.badRequest("game_over", "The game is over");
    return g;
  }

  private static int colourOrThrow(LiveGame g, long userId) {
    int colour = g.colourOf(userId);
    if (colour < 0) throw ApiException.forbidden("not_player", "You are not playing this game");
    return colour;
  }

  public static ClockView clock(LiveGame g) {
    long now = System.currentTimeMillis();
    ChessClock c = g.clock();
    return new ClockView(
        Math.max(0, c.remaining(0, now)), Math.max(0, c.remaining(1, now)),
        c.running() < 0 ? null : Colour.name(c.running()), c.initialMs(), c.incrementMs(), now);
  }

  private static PlayerView playerView(LiveGame.Player p, Integer diff) {
    return new PlayerView(p.userId(), p.username(), p.title(), p.rating(), diff, p.provisional(),
        p.engineLevel());
  }

  private static PlayerView playerView(User u, Integer rating, Integer diff, Integer engineLevel) {
    boolean engine = User.ENGINE_USERNAME.equals(u.getUsername());
    return new PlayerView(u.getId(), u.getUsername(), u.getTitle(), rating, diff, false,
        engine ? engineLevel : null);
  }

  /** SAN moves of a stored game (replays the UCI moves on the v1 board). */
  static List<String> sans(String startFen, List<String> uciMoves) {
    Position p = Position.fromFen(startFen);
    List<String> out = new ArrayList<>(uciMoves.size());
    for (String m : uciMoves) {
      int move = phase2.UciMoveParser.parse(p, m);
      out.add(San.toSan(p, move));
      p.makeMove(move);
    }
    return out;
  }
}
