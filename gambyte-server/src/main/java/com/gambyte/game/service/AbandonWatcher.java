package com.gambyte.game.service;

import com.gambyte.auth.AuthUser;
import com.gambyte.game.live.LiveGame;
import com.gambyte.game.live.LiveGameStore;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Ends games where a player left. When a player's last connection closes during a game we note
 * the time; if they don't come back within {@link #GRACE_MS} while the opponent is still there,
 * the opponent wins ("left the game"). A page reload or a short network drop reconnects well
 * within the grace period and changes nothing.
 */
@Component
public class AbandonWatcher {

  static final long GRACE_MS = 60_000;

  private final Map<Long, Long> goneSince = new ConcurrentHashMap<>();
  private final LiveGameStore live;
  private final GameService games;
  private final SimpUserRegistry connected;

  public AbandonWatcher(LiveGameStore live, GameService games, SimpUserRegistry connected) {
    this.live = live;
    this.games = games;
    this.connected = connected;
  }

  @EventListener
  public void onDisconnect(SessionDisconnectEvent e) {
    Long id = userId(e.getUser());
    if (id == null) return;
    // Other tabs may still be open: only "gone" once no connection remains.
    var user = connected.getUser(String.valueOf(e.getUser().getName()));
    if (user == null || user.getSessions().size() <= 1) goneSince.put(id, System.currentTimeMillis());
  }

  @EventListener
  public void onConnect(SessionConnectedEvent e) {
    Long id = userId(e.getUser());
    if (id != null) goneSince.remove(id);
  }

  @Scheduled(fixedDelay = 5_000)
  public void check() {
    long now = System.currentTimeMillis();
    for (LiveGame g : live.all()) {
      if (g.status().isOver() || g.clock().isUntimed() || g.moves().size() < 2) continue;
      for (int colour = 0; colour < 2; colour++) {
        LiveGame.Player p = g.player(colour);
        LiveGame.Player opp = g.player(1 - colour);
        if (p.isEngine() || opp.isEngine()) continue;
        Long since = goneSince.get(p.userId());
        boolean opponentHere = !goneSince.containsKey(opp.userId());
        if (since != null && opponentHere && now - since > GRACE_MS) {
          g.abandon(colour);
          games.finish(g);
        }
      }
    }
  }

  private static Long userId(Principal principal) {
    if (principal instanceof Authentication a && a.getPrincipal() instanceof AuthUser u) return u.id();
    return null;
  }
}
