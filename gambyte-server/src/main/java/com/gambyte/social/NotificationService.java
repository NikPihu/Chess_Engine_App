package com.gambyte.social;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * In-app notifications: stored in Postgres (so they survive being offline) and pushed live over
 * WebSocket to {@code /user/queue/notify} when the user is connected.
 */
@Service
public class NotificationService {

  public record Notification(long id, String kind, String text, String link, boolean read, Instant createdAt) {}

  private final JdbcTemplate jdbc;
  private final SimpMessagingTemplate ws;

  public NotificationService(JdbcTemplate jdbc, SimpMessagingTemplate ws) {
    this.jdbc = jdbc;
    this.ws = ws;
  }

  /** Stores and pushes a notification. {@code username} is used for the live push. */
  public void notify(long userId, String username, String kind, String text, String link) {
    Long id = jdbc.queryForObject(
        "INSERT INTO notifications (user_id, kind, text, link) VALUES (?, ?, ?, ?) RETURNING id",
        Long.class, userId, kind, text, link);
    ws.convertAndSendToUser(username, "/queue/notify",
        new Notification(id == null ? 0 : id, kind, text, link, false, Instant.now()));
  }

  public List<Notification> recent(long userId) {
    return jdbc.query(
        "SELECT id, kind, text, link, read, created_at FROM notifications WHERE user_id = ?"
            + " ORDER BY created_at DESC LIMIT 30",
        (rs, i) -> new Notification(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
            rs.getBoolean(5), rs.getTimestamp(6).toInstant()),
        userId);
  }

  public int unread(long userId) {
    Integer n = jdbc.queryForObject(
        "SELECT count(*) FROM notifications WHERE user_id = ? AND NOT read", Integer.class, userId);
    return n == null ? 0 : n;
  }

  public void markAllRead(long userId) {
    jdbc.update("UPDATE notifications SET read = TRUE WHERE user_id = ? AND NOT read", userId);
  }

  /** Notifications older than 60 days are deleted nightly to keep the table small. */
  @Scheduled(cron = "0 30 3 * * *")
  public void purgeOld() {
    jdbc.update("DELETE FROM notifications WHERE created_at < ?",
        Timestamp.from(Instant.now().minusSeconds(60L * 24 * 3600)));
  }

  public Map<String, Object> summary(long userId) {
    return Map.of("unread", unread(userId), "items", recent(userId));
  }
}
