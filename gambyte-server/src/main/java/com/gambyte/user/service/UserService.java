package com.gambyte.user.service;

import com.gambyte.common.ApiException;
import com.gambyte.common.Tokens;
import com.gambyte.config.GambyteProperties;
import com.gambyte.notification.MailService;
import com.gambyte.user.api.UserDtos;
import com.gambyte.user.api.UserDtos.LeaderboardEntry;
import com.gambyte.user.api.UserDtos.Me;
import com.gambyte.user.api.UserDtos.Profile;
import com.gambyte.user.api.UserDtos.RatingView;
import com.gambyte.user.api.UserDtos.UserRef;
import com.gambyte.user.domain.EmailToken;
import com.gambyte.user.domain.Perf;
import com.gambyte.user.domain.Rating;
import com.gambyte.user.domain.User;
import com.gambyte.user.repo.EmailTokenRepository;
import com.gambyte.user.repo.RatingRepository;
import com.gambyte.user.repo.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything about accounts: registration, email verification, password reset, profiles,
 * settings, ratings and leaderboards.
 *
 * <p>This is the only class that writes {@code users} and {@code ratings}. Other features call its
 * methods (for example the rating feature uses {@link #ratingFor} and {@link #saveRating}).
 */
@Service
public class UserService {

  private final UserRepository users;
  private final RatingRepository ratings;
  private final EmailTokenRepository tokens;
  private final PasswordEncoder passwords;
  private final MailService mail;
  private final PresenceService presence;
  private final GambyteProperties props;

  public UserService(
      UserRepository users,
      RatingRepository ratings,
      EmailTokenRepository tokens,
      PasswordEncoder passwords,
      MailService mail,
      PresenceService presence,
      GambyteProperties props) {
    this.users = users;
    this.ratings = ratings;
    this.tokens = tokens;
    this.passwords = passwords;
    this.mail = mail;
    this.presence = presence;
    this.props = props;
  }

  // ------------------------------------------------------------------ registration and login

  @Transactional
  public User register(String username, String email, String rawPassword) {
    if (users.existsByUsername(username)) {
      throw ApiException.conflict("username_taken", "That username is taken");
    }
    if (users.existsByEmail(email)) {
      throw ApiException.conflict("email_taken", "An account with that email already exists");
    }
    User user = users.save(new User(username, email.trim(), passwords.encode(rawPassword)));
    sendVerification(user);
    return user;
  }

  public void sendVerification(User user) {
    String token = createToken(user.getId(), EmailToken.Purpose.VERIFY, Duration.ofDays(3));
    mail.send(
        user.getEmail(),
        "Welcome to Gambyte - confirm your email",
        "Hi %s,%n%nConfirm your email address:%n%s/verify-email?token=%s%n%nHave fun!%nGambyte"
            .formatted(user.getUsername(), props.siteUrl(), token));
  }

  @Transactional
  public void verifyEmail(String token) {
    EmailToken t = consumeToken(token, EmailToken.Purpose.VERIFY);
    users.findById(t.getUserId()).ifPresent(u -> u.setEmailVerified(true));
  }

  /** Always succeeds, even for unknown emails, so the endpoint can't be used to find accounts. */
  @Transactional
  public void forgotPassword(String email) {
    users
        .findByEmail(email)
        .filter(u -> u.getPasswordHash() != null)
        .ifPresent(
            u -> {
              String token = createToken(u.getId(), EmailToken.Purpose.RESET, Duration.ofHours(1));
              mail.send(
                  u.getEmail(),
                  "Reset your Gambyte password",
                  ("Hi %s,%n%nSet a new password (the link works for one hour):%n"
                          + "%s/reset-password?token=%s%n%n"
                          + "If you did not ask for this, ignore this email.")
                      .formatted(u.getUsername(), props.siteUrl(), token));
            });
  }

  @Transactional
  public void resetPassword(String token, String newPassword) {
    EmailToken t = consumeToken(token, EmailToken.Purpose.RESET);
    User u = get(t.getUserId());
    u.setPasswordHash(passwords.encode(newPassword));
    u.setEmailVerified(true); // they proved they own the mailbox
  }

  @Transactional
  public void changePassword(long userId, String current, String next) {
    User u = get(userId);
    if (u.getPasswordHash() == null || !passwords.matches(current, u.getPasswordHash())) {
      throw ApiException.badRequest("wrong_password", "Current password is wrong");
    }
    u.setPasswordHash(passwords.encode(next));
  }

  private String createToken(long userId, EmailToken.Purpose purpose, Duration validFor) {
    String token = Tokens.secret();
    tokens.save(
        new EmailToken(Tokens.sha256(token), userId, purpose, Instant.now().plus(validFor)));
    return token;
  }

  private EmailToken consumeToken(String token, EmailToken.Purpose purpose) {
    EmailToken t =
        tokens
            .findById(Tokens.sha256(token))
            .filter(x -> x.getPurpose() == purpose && !x.isExpired())
            .orElseThrow(
                () -> ApiException.badRequest("invalid_token", "This link is invalid or expired"));
    tokens.delete(t);
    return t;
  }

  @Scheduled(cron = "0 17 * * * *") // hourly
  @Transactional
  public void purgeExpiredTokens() {
    tokens.deleteExpired();
  }

  // ------------------------------------------------------------------ reading accounts

  /** The account for a login name (username or email), if any. Used by Spring Security. */
  public java.util.Optional<User> findForLogin(String usernameOrEmail) {
    return users.findByLogin(usernameOrEmail);
  }

  public User get(long id) {
    return users.findById(id).orElseThrow(() -> ApiException.notFound("user"));
  }

  public User getByUsername(String username) {
    return users.findByUsername(username).orElseThrow(() -> ApiException.notFound("user"));
  }

  /** Several users by id in one query (avoids one query per player, the "N+1" problem). */
  public Map<Long, User> getAll(java.util.Collection<Long> ids) {
    return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
  }

  /** The built-in engine's account (see migration V1). */
  @Cacheable("engineUser")
  public UserRef engineUser() {
    return UserRef.of(getByUsername(User.ENGINE_USERNAME));
  }

  public UserRef ref(long id) {
    return UserRef.of(get(id));
  }

  @Transactional(readOnly = true)
  public Me me(long id) {
    User u = get(id);
    return new Me(
        u.getId(),
        u.getUsername(),
        u.getEmail(),
        u.isEmailVerified(),
        u.getRole().name(),
        u.getTitle(),
        u.isPremium(),
        u.getPremiumUntil(),
        u.getSettings(),
        UserDtos.ratingsMap(ratings.findByUserId(id)));
  }

  @Transactional(readOnly = true)
  public Profile profile(String username) {
    User u = getByUsername(username);
    if (u.isBanned()) throw ApiException.notFound("user");
    boolean engine = User.ENGINE_USERNAME.equals(u.getUsername()); // always available
    return new Profile(
        u.getId(),
        u.getUsername(),
        u.getTitle(),
        u.getCountry(),
        u.getBio(),
        u.isPremium(),
        engine || presence.isOnline(u.getId()),
        u.getCreatedAt(),
        u.getLastSeenAt(),
        UserDtos.ratingsMap(ratings.findByUserId(u.getId())));
  }

  public List<UserRef> search(String prefix) {
    if (prefix == null || prefix.length() < 2) return List.of();
    return users.searchByPrefix(prefix, PageRequest.of(0, 10)).stream().map(UserRef::of).toList();
  }

  // ------------------------------------------------------------------ profile and settings

  @Transactional
  public void updateProfile(long userId, String bio, String country) {
    User u = get(userId);
    u.setBio(bio == null || bio.isBlank() ? null : bio.strip());
    u.setCountry(country == null || country.isBlank() ? null : country);
  }

  /** Settings are a small JSON object owned by the frontend (themes, sounds, premoves...). */
  @Transactional
  public Map<String, Object> updateSettings(long userId, Map<String, Object> settings) {
    if (settings.toString().length() > 4000) {
      throw ApiException.badRequest("settings_too_large", "Settings are too large");
    }
    get(userId).setSettings(settings);
    return settings;
  }

  @Transactional
  public void touchLastSeen(long userId) {
    users.findById(userId).ifPresent(u -> u.setLastSeenAt(Instant.now()));
  }

  // ------------------------------------------------------------------ staff and billing hooks

  @Transactional
  public void setPremiumUntil(long userId, Instant until) {
    get(userId).setPremiumUntil(until);
  }

  @Transactional
  public void setBanned(long userId, boolean banned) {
    get(userId).setBanned(banned);
  }

  @Transactional
  public void setMuted(long userId, boolean muted) {
    get(userId).setMuted(muted);
  }

  @Transactional
  public void makeBot(long userId) {
    User u = get(userId);
    if (u.isBot()) return;
    u.setTitle(User.TITLE_BOT);
  }

  // ------------------------------------------------------------------ ratings

  /** The user's rating in a pool; a fresh 1500 ± 350 rating if they never played it. */
  @Transactional
  public Rating ratingFor(long userId, Perf perf) {
    return ratings
        .findById(new Rating.Key(userId, perf.key()))
        .orElseGet(() -> ratings.save(new Rating(userId, perf)));
  }

  @Transactional
  @CacheEvict(value = "leaderboard", allEntries = true)
  public void saveRating(Rating rating) {
    ratings.save(rating);
  }

  /** Top 100 of a pool. Cached (see spring.cache in application.yml). */
  @Cacheable(value = "leaderboard", key = "#perf.key()")
  @Transactional(readOnly = true)
  public List<LeaderboardEntry> leaderboard(Perf perf) {
    List<Rating> top = ratings.leaderboard(perf.key(), PageRequest.of(0, 100));
    Map<Long, User> byId = getAll(top.stream().map(Rating::userId).toList());
    List<LeaderboardEntry> out = new ArrayList<>();
    for (Rating r : top) {
      User u = byId.get(r.userId());
      if (u == null || u.isBanned()) continue;
      out.add(new LeaderboardEntry(out.size() + 1, UserRef.of(u), RatingView.of(r)));
    }
    return out;
  }
}
