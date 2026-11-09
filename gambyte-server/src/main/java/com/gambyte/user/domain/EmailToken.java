package com.gambyte.user.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A one-time link token for email verification or password reset. Only the SHA-256 hash of the
 * token is stored; the plain token exists only inside the email that was sent.
 */
@Entity
@Table(name = "email_tokens")
public class EmailToken {

  /** What the link does. */
  public enum Purpose {
    VERIFY,
    RESET
  }

  @Id private String tokenHash;
  private long userId;

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  private Purpose purpose;

  private Instant expiresAt;

  protected EmailToken() {}

  public EmailToken(String tokenHash, long userId, Purpose purpose, Instant expiresAt) {
    this.tokenHash = tokenHash;
    this.userId = userId;
    this.purpose = purpose;
    this.expiresAt = expiresAt;
  }

  public long getUserId() {
    return userId;
  }

  public Purpose getPurpose() {
    return purpose;
  }

  public boolean isExpired() {
    return expiresAt.isBefore(Instant.now());
  }
}
