package com.gambyte.game.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Request and response bodies of the game API (REST and WebSocket). */
public final class GameDtos {
  private GameDtos() {}

  /** A new game against Gambyte Engine. {@code initial} = 0 and {@code increment} = 0: no clock. */
  public record ComputerGameRequest(
      @Min(1) @Max(8) int level,
      @Pattern(regexp = "white|black|random") String color,
      @Min(0) @Max(10800) int initial,
      @Min(0) @Max(180) int increment,
      @Size(max = 100) String fen) {}

  /** A move in UCI notation ("e2e4", "e7e8q"), with the client's measured network lag. */
  public record MoveRequest(@NotBlank @Size(max = 5) String uci, Long lag) {}

  public record ChatRequest(@NotBlank @Size(max = 140) String text) {}

  /** One side of a game as shown on screen. */
  public record PlayerView(
      long id,
      String username,
      String title,
      Integer rating,
      Integer ratingDiff,
      boolean provisional,
      Integer engineLevel) {}

  /** Clock state: remaining ms for both sides, which one runs, and the server time it was read. */
  public record ClockView(
      long white, long black, String running, long initial, long increment, long serverTime) {}

  /**
   * Everything needed to draw a game, live or finished. The client also receives this as the
   * reconnect snapshot: after a reload it simply asks for it again.
   */
  public record GameView(
      String id,
      PlayerView white,
      PlayerView black,
      String perf,
      boolean rated,
      String source,
      String status,
      String winner,
      String termination,
      String startFen,
      String fen,
      List<String> moves,
      List<String> sans,
      List<Integer> moveTimes,
      ClockView clock,
      String turn,
      String drawOffer,
      String rematchOffer,
      int version,
      Long tournamentId,
      Instant createdAt,
      Instant endedAt) {}

  /** A row in a player's game list. */
  public record GameSummary(
      String id,
      PlayerView white,
      PlayerView black,
      String perf,
      boolean rated,
      String status,
      String winner,
      String termination,
      int ply,
      String finalFen,
      int initial,
      int increment,
      Instant createdAt) {}

  public record GamePage(List<GameSummary> games, Instant nextBefore) {}

  // ------------------------------------------------------------------ WebSocket events

  /**
   * Messages on {@code /topic/game/{id}}. {@code t} tells the client what kind it is: "move",
   * "end", "draw", "rematch", "chat", "takeback" or "state" (send the full view again).
   */
  public record GameEvent(
      String t,
      int v,
      String uci,
      String san,
      String fen,
      Integer ply,
      ClockView clock,
      String status,
      String winner,
      String termination,
      String by,
      Integer whiteRatingDiff,
      Integer blackRatingDiff,
      String text,
      String rematchId) {

    public static GameEvent move(int v, String uci, String san, String fen, int ply, ClockView c) {
      return new GameEvent("move", v, uci, san, fen, ply, c, null, null, null, null, null, null, null, null);
    }

    public static GameEvent end(int v, String status, String winner, String termination,
        ClockView c, Integer wDiff, Integer bDiff) {
      return new GameEvent("end", v, null, null, null, null, c, status, winner, termination,
          null, wDiff, bDiff, null, null);
    }

    public static GameEvent simple(String t, int v, String by) {
      return new GameEvent(t, v, null, null, null, null, null, null, null, null, by, null, null, null, null);
    }

    public static GameEvent chat(String by, String text) {
      return new GameEvent("chat", 0, null, null, null, null, null, null, null, null, by, null, null, text, null);
    }

    public static GameEvent rematchReady(String newGameId) {
      return new GameEvent("rematch", 0, null, null, null, null, null, null, null, null, null, null, null, null, newGameId);
    }
  }
}
