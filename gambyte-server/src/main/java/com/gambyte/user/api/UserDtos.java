package com.gambyte.user.api;

import com.gambyte.user.domain.Rating;
import com.gambyte.user.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Request and response bodies of the user/auth API. Records keep them immutable and tiny; entities
 * never leave the server, so the JSON shape is stable even if the database changes.
 */
public final class UserDtos {
  private UserDtos() {}

  /** Usernames: 3-20 letters, digits, _ or -, starting with a letter or digit (like Lichess). */
  public static final String USERNAME_REGEX = "^[A-Za-z0-9][A-Za-z0-9_-]{2,19}$";

  public record RegisterRequest(
      @NotBlank @Pattern(regexp = USERNAME_REGEX, message = "3-20 letters, digits, _ or -")
          String username,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 8, max = 128, message = "at least 8 characters") String password) {}

  public record LoginRequest(@NotBlank String login, @NotBlank String password) {}

  public record ForgotPasswordRequest(@NotBlank @Email String email) {}

  public record ResetPasswordRequest(
      @NotBlank String token, @NotBlank @Size(min = 8, max = 128) String password) {}

  public record TokenRequest(@NotBlank String token) {}

  public record ChangePasswordRequest(
      @NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 128) String newPassword) {}

  public record UpdateProfileRequest(
      @Size(max = 400) String bio, @Pattern(regexp = "^[A-Z]{2}$|^$") String country) {}

  /** A rating as shown in the UI. */
  public record RatingView(int rating, int deviation, boolean provisional, int games, int best) {
    public static RatingView of(Rating r) {
      return new RatingView(
          (int) Math.round(r.getRating()),
          (int) Math.round(r.getDeviation()),
          r.isProvisional(),
          r.getGames(),
          r.getBest());
    }
  }

  /** Small user reference used everywhere (game players, friends lists, leaderboards). */
  public record UserRef(long id, String username, String title, boolean premium) {
    public static UserRef of(User u) {
      return new UserRef(u.getId(), u.getUsername(), u.getTitle(), u.isPremium());
    }
  }

  /** The logged-in user's own account (GET /api/auth/me). */
  public record Me(
      long id,
      String username,
      String email,
      boolean emailVerified,
      String role,
      String title,
      boolean premium,
      Instant premiumUntil,
      Map<String, Object> settings,
      Map<String, RatingView> ratings) {}

  /** A public profile page. */
  public record Profile(
      long id,
      String username,
      String title,
      String country,
      String bio,
      boolean premium,
      boolean online,
      Instant createdAt,
      Instant lastSeenAt,
      Map<String, RatingView> ratings) {}

  public record LeaderboardEntry(int rank, UserRef user, RatingView rating) {}

  public record Leaderboard(String perf, List<LeaderboardEntry> entries) {}

  public static Map<String, RatingView> ratingsMap(List<Rating> ratings) {
    Map<String, RatingView> out = new TreeMap<>();
    for (Rating r : ratings) out.put(r.perf().key(), RatingView.of(r));
    return out;
  }
}
