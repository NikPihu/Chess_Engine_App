package com.gambyte.social;

import com.gambyte.common.ApiException;
import com.gambyte.lobby.ChallengeService.ChallengeCreated;
import com.gambyte.user.api.UserDtos.UserRef;
import com.gambyte.user.domain.User;
import com.gambyte.user.service.PresenceService;
import com.gambyte.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Follows/friends, the friends list with who is online, and player reports. */
@Service
public class SocialService {

  public record Friend(UserRef user, boolean online, boolean mutual) {}

  private final JdbcTemplate jdbc;
  private final UserService users;
  private final PresenceService presence;
  private final NotificationService notifications;

  public SocialService(
      JdbcTemplate jdbc, UserService users, PresenceService presence, NotificationService notifications) {
    this.jdbc = jdbc;
    this.users = users;
    this.presence = presence;
    this.notifications = notifications;
  }

  public void follow(long me, String username) {
    User target = users.getByUsername(username);
    if (target.getId() == me) throw ApiException.badRequest("self_follow", "You cannot follow yourself");
    int added = jdbc.update(
        "INSERT INTO follows (follower_id, followee_id) VALUES (?, ?) ON CONFLICT DO NOTHING", me, target.getId());
    if (added > 0 && !target.isBot()) {
      User u = users.get(me);
      notifications.notify(target.getId(), target.getUsername(), "follow",
          u.getUsername() + " started following you", "/@/" + u.getUsername());
    }
  }

  public void unfollow(long me, String username) {
    jdbc.update("DELETE FROM follows WHERE follower_id = ? AND followee_id = ?", me, users.getByUsername(username).getId());
  }

  public boolean isFollowing(long me, String username) {
    Integer n = jdbc.queryForObject("SELECT count(*) FROM follows WHERE follower_id = ? AND followee_id = ?",
        Integer.class, me, users.getByUsername(username).getId());
    return n != null && n > 0;
  }

  /** Everyone I follow, online first, marking mutual follows ("friends"). */
  public List<Friend> following(long me) {
    List<Long> ids = jdbc.queryForList("SELECT followee_id FROM follows WHERE follower_id = ?", Long.class, me);
    Set<Long> followBack = Set.copyOf(jdbc.queryForList(
        "SELECT follower_id FROM follows WHERE followee_id = ?", Long.class, me));
    Set<Long> online = presence.online(ids);
    Map<Long, User> people = users.getAll(ids);
    return ids.stream()
        .filter(people::containsKey)
        .map(id -> new Friend(UserRef.of(people.get(id)), online.contains(id), followBack.contains(id)))
        .sorted((a, b) -> Boolean.compare(b.online(), a.online()))
        .toList();
  }

  public void report(long me, String username, String reason, String text, String gameId) {
    User target = users.getByUsername(username);
    if (!List.of("cheat", "abuse", "other").contains(reason)) {
      throw ApiException.badRequest("bad_reason", "Reason must be cheat, abuse or other");
    }
    jdbc.update("INSERT INTO reports (reporter_id, reported_id, reason, text, game_id) VALUES (?, ?, ?, ?, ?)",
        me, target.getId(), reason, text, gameId);
  }

  /** Challenges also show up in the notification bell. */
  @EventListener
  public void onChallenge(ChallengeCreated e) {
    var c = e.challenge();
    if (c.destId() == null) return;
    String tc = c.initial() == 0 ? "unlimited" : (c.initial() / 60) + "+" + c.increment();
    notifications.notify(c.destId(), c.dest(), "challenge",
        c.challenger() + " challenges you (" + tc + (c.rated() ? ", rated" : "") + ")", "/challenge/" + c.id());
  }
}
