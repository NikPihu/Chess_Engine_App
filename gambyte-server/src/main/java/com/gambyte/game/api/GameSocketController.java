package com.gambyte.game.api;

import com.gambyte.auth.AuthUser;
import com.gambyte.common.ApiException;
import com.gambyte.game.api.GameDtos.MoveRequest;
import com.gambyte.game.service.GameService;
import com.gambyte.user.service.PresenceService;
import java.security.Principal;
import java.util.Map;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/**
 * WebSocket (STOMP) messages from browsers. Sending moves here instead of over REST saves an HTTP
 * round trip per move, which matters in bullet.
 *
 * <ul>
 *   <li>{@code /app/game/{id}/move} {"uci":"e2e4","lag":45}
 *   <li>{@code /app/game/{id}/blur} the tab lost focus (fair-play signal)
 *   <li>{@code /app/ping} heartbeat: keeps the user "online"
 * </ul>
 *
 * Errors go back privately to {@code /user/queue/errors}.
 */
@Controller
public class GameSocketController {

  private final GameService games;
  private final PresenceService presence;

  public GameSocketController(GameService games, PresenceService presence) {
    this.games = games;
    this.presence = presence;
  }

  @MessageMapping("/game/{id}/move")
  public void move(@DestinationVariable String id, @Payload MoveRequest body, Principal principal) {
    games.move(id, user(principal).id(), body.uci(), body.lag() == null ? 0 : body.lag());
  }

  @MessageMapping("/game/{id}/blur")
  public void blur(@DestinationVariable String id, Principal principal) {
    games.blur(id, user(principal).id());
  }

  @MessageMapping("/ping")
  public void ping(Principal principal) {
    if (principal != null) presence.heartbeat(user(principal).id());
  }

  @MessageExceptionHandler(ApiException.class)
  @SendToUser("/queue/errors")
  public Map<String, String> error(ApiException e) {
    return Map.of("code", e.code(), "message", e.getMessage());
  }

  private static AuthUser user(Principal principal) {
    if (principal instanceof Authentication auth && auth.getPrincipal() instanceof AuthUser u) {
      return u;
    }
    throw ApiException.forbidden("login_required", "Log in to play");
  }
}
