package com.gambyte.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A Gambyte account: a human player, a bot account, or the built-in engine ({@link
 * #ENGINE_USERNAME}).
 *
 * <p>Mutable JPA entity; only the {@code user} feature changes it. Other features read users
 * through {@code UserService} and get {@code UserSummary} DTOs back.
 */
@Entity
@Table(name = "users")
public class User {

  /** Username of the system account the built-in engine plays as (created by migration V1). */
  public static final String ENGINE_USERNAME = "GambyteEngine";

  /** Title shown before bot names, as on Lichess. */
  public static final String TITLE_BOT = "BOT";

  /** What the account may do. Premium is not a role: it is {@link #premiumUntil}. */
  public enum Role {
    USER,
    MOD,
    ADMIN
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 30)
  private String username;

  private String email;
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role = Role.USER;

  private String title;
  private String country;
  private String bio;
  private boolean emailVerified;
  private boolean banned;
  private boolean muted;
  private Instant premiumUntil;

  /** Free-form UI preferences (board theme, piece set, sounds, premoves...). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, Object> settings = new HashMap<>();

  @Column(nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  private Instant lastSeenAt;

  protected User() {} // for JPA

  public User(String username, String email, String passwordHash) {
    this.username = username;
    this.email = email;
    this.passwordHash = passwordHash;
  }

  public boolean isBot() {
    return TITLE_BOT.equals(title);
  }

  public boolean isPremium() {
    return premiumUntil != null && premiumUntil.isAfter(Instant.now());
  }

  public Long getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getBio() {
    return bio;
  }

  public void setBio(String bio) {
    this.bio = bio;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }

  public void setEmailVerified(boolean emailVerified) {
    this.emailVerified = emailVerified;
  }

  public boolean isBanned() {
    return banned;
  }

  public void setBanned(boolean banned) {
    this.banned = banned;
  }

  public boolean isMuted() {
    return muted;
  }

  public void setMuted(boolean muted) {
    this.muted = muted;
  }

  public Instant getPremiumUntil() {
    return premiumUntil;
  }

  public void setPremiumUntil(Instant premiumUntil) {
    this.premiumUntil = premiumUntil;
  }

  public Map<String, Object> getSettings() {
    return settings;
  }

  public void setSettings(Map<String, Object> settings) {
    this.settings = settings;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public void setLastSeenAt(Instant lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
  }
}
