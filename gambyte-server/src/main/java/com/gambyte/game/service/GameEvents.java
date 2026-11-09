package com.gambyte.game.service;

import com.gambyte.game.api.GameDtos.GameEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Broadcasts what happens in a game.
 *
 * <ul>
 *   <li>to browsers (players and spectators) over WebSocket on {@code /topic/game/{id}}
 *   <li>to other server features as Spring events: {@link GameChanged} (the bot API streams these
 *       to bots) and {@link GameFinished} (ratings, game review, fair play, tournaments react)
 * </ul>
 */
@Component
public class GameEvents {

  /** Any change in a running game (a move, a draw offer, the end). */
  public record GameChanged(String gameId, GameEvent event) {}

  /** A game has ended and been saved to the database. */
  public record GameFinished(
      String gameId, long whiteId, long blackId, boolean rated, String winner, Long tournamentId) {}

  private final SimpMessagingTemplate ws;
  private final ApplicationEventPublisher spring;

  public GameEvents(SimpMessagingTemplate ws, ApplicationEventPublisher spring) {
    this.ws = ws;
    this.spring = spring;
  }

  public void publish(String gameId, GameEvent event) {
    ws.convertAndSend("/topic/game/" + gameId, event);
    spring.publishEvent(new GameChanged(gameId, event));
  }

  public void finished(GameFinished event) {
    spring.publishEvent(event);
  }

  /** A private message to one user (match found, challenge received...). */
  public void toUser(String username, String destination, Object payload) {
    ws.convertAndSendToUser(username, destination, payload);
  }
}
