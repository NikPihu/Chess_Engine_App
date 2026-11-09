package com.gambyte.admin;

import com.gambyte.auth.AuthUser;
import com.gambyte.common.ApiException;
import com.gambyte.puzzle.PuzzleImporter;
import com.gambyte.social.NotificationService;
import com.gambyte.user.domain.User;
import com.gambyte.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Moderation and administration. Every path is under {@code /api/admin}, which SecurityConfig
 * restricts to moderators; actions that change money or roles additionally require ADMIN. Every
 * action is logged with the moderator's name (an audit trail in the server log).
 */
@RestController
@Tag(name = "Admin", description = "Moderation (MOD) and administration (ADMIN)")
public class AdminController {

  private static final Logger audit = LoggerFactory.getLogger("gambyte.audit");

  private final JdbcTemplate jdbc;
  private final UserService users;
  private final NotificationService notifications;
  private final PuzzleImporter importer;

  public AdminController(JdbcTemplate jdbc, UserService users, NotificationService notifications, PuzzleImporter importer) {
    this.jdbc = jdbc;
    this.users = users;
    this.notifications = notifications;
    this.importer = importer;
  }

  @Operation(summary = "Site numbers for the dashboard")
  @GetMapping("/api/admin/stats")
  public Map<String, Object> stats() {
    return jdbc.queryForMap("""
        SELECT (SELECT count(*) FROM users) AS users,
               (SELECT count(*) FROM users WHERE created_at > now() - interval '1 day') AS new_users_today,
               (SELECT count(*) FROM games WHERE created_at > now() - interval '1 day') AS games_today,
               (SELECT count(*) FROM users WHERE premium_until > now()) AS premium_users,
               (SELECT count(*) FROM puzzles) AS puzzles,
               (SELECT count(*) FROM reports WHERE status = 'OPEN') AS open_reports,
               (SELECT count(*) FROM fairplay_checks WHERE flagged AND NOT reviewed) AS open_flags""");
  }

  @Operation(summary = "Games flagged by the fair-play analyser, not yet reviewed")
  @GetMapping("/api/admin/flags")
  public List<Map<String, Object>> flags() {
    return jdbc.queryForList("""
        SELECT f.game_id, f.user_id, u.username, f.moves_checked, f.acpl, f.top_match, f.time_cv, f.blurs,
               f.rating, f.suspicion, f.created_at,
               (SELECT count(*) FROM fairplay_checks x WHERE x.user_id = f.user_id AND x.flagged) AS total_flags
        FROM fairplay_checks f JOIN users u ON u.id = f.user_id
        WHERE f.flagged AND NOT f.reviewed ORDER BY f.suspicion DESC LIMIT 100""");
  }

  @PostMapping("/api/admin/flags/{gameId}/{userId}/reviewed")
  public ResponseEntity<Void> flagReviewed(
      @AuthenticationPrincipal AuthUser me, @PathVariable String gameId, @PathVariable long userId) {
    jdbc.update("UPDATE fairplay_checks SET reviewed = TRUE WHERE game_id = ? AND user_id = ?", gameId, userId);
    audit.info("{} reviewed fair-play flag {}/{}", me.username(), gameId, userId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/admin/reports")
  public List<Map<String, Object>> reports() {
    return jdbc.queryForList("""
        SELECT r.id, r.reason, r.text, r.game_id, r.created_at, a.username AS reporter, b.username AS reported
        FROM reports r JOIN users a ON a.id = r.reporter_id JOIN users b ON b.id = r.reported_id
        WHERE r.status = 'OPEN' ORDER BY r.created_at LIMIT 100""");
  }

  public record CloseReport(String resolution) {}

  @PostMapping("/api/admin/reports/{id}/close")
  public ResponseEntity<Void> closeReport(
      @AuthenticationPrincipal AuthUser me, @PathVariable long id, @RequestBody CloseReport body) {
    jdbc.update("UPDATE reports SET status = 'CLOSED', resolution = ? WHERE id = ?", body.resolution(), id);
    audit.info("{} closed report {}: {}", me.username(), id, body.resolution());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "ban | unban | mute | unmute")
  @PostMapping("/api/admin/users/{username}/{action}")
  public ResponseEntity<Void> moderate(
      @AuthenticationPrincipal AuthUser me, @PathVariable String username, @PathVariable String action) {
    User u = users.getByUsername(username);
    if (u.getRole() != User.Role.USER) throw ApiException.forbidden("staff", "Staff accounts are managed by an admin");
    switch (action) {
      case "ban" -> users.setBanned(u.getId(), true);
      case "unban" -> users.setBanned(u.getId(), false);
      case "mute" -> users.setMuted(u.getId(), true);
      case "unmute" -> users.setMuted(u.getId(), false);
      default -> throw ApiException.badRequest("bad_action", "Unknown action");
    }
    audit.info("{} did {} on {}", me.username(), action, username);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Give premium for N days (support / promotions). ADMIN only.")
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/api/admin/users/{username}/premium")
  public ResponseEntity<Void> grantPremium(
      @AuthenticationPrincipal AuthUser me, @PathVariable String username, @RequestParam int days) {
    User u = users.getByUsername(username);
    Instant base = u.isPremium() ? u.getPremiumUntil() : Instant.now();
    users.setPremiumUntil(u.getId(), base.plus(Duration.ofDays(days)));
    notifications.notify(u.getId(), u.getUsername(), "premium", "You received " + days + " days of Premium!", "/premium");
    audit.info("{} granted {} days premium to {}", me.username(), days, username);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Set a role (USER, MOD, ADMIN). ADMIN only.")
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/api/admin/users/{username}/role")
  public ResponseEntity<Void> setRole(
      @AuthenticationPrincipal AuthUser me, @PathVariable String username, @RequestParam String role) {
    User u = users.getByUsername(username);
    jdbc.update("UPDATE users SET role = ? WHERE id = ?", User.Role.valueOf(role).name(), u.getId());
    audit.info("{} set role {} for {}", me.username(), role, username);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Import puzzles from a CSV on the server's disk (Gambyte or Lichess format). ADMIN only.")
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/api/admin/puzzles/import")
  public Map<String, Integer> importPuzzles(
      @AuthenticationPrincipal AuthUser me, @RequestParam String path, @RequestParam(defaultValue = "100000") int max)
      throws Exception {
    int n = importer.importFile(Path.of(path), max);
    audit.info("{} imported {} puzzles from {}", me.username(), n, path);
    return Map.of("imported", n);
  }

  @GetMapping("/api/admin/users/{username}/games")
  public List<Map<String, Object>> recentGames(@PathVariable String username) {
    long id = users.getByUsername(username).getId();
    return jdbc.queryForList("""
        SELECT id, perf, rated, status, winner, white_id, black_id, created_at FROM games
        WHERE white_id = ? OR black_id = ? ORDER BY created_at DESC LIMIT 50""", id, id);
  }

  /** Not an endpoint: used by AdminBootstrap. */
  void promote(String username) {
    jdbc.update("UPDATE users SET role = 'ADMIN' WHERE lower(username) = lower(?)", username);
  }

  static Timestamp now() {
    return Timestamp.from(Instant.now());
  }
}
