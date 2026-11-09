package com.gambyte.bot;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A personal API token. The token itself is never stored, only its SHA-256 hash. */
@Entity
@Table(name = "api_tokens")
public class ApiToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private long userId;
  private String tokenHash;
  private String name;
  private Instant createdAt = Instant.now();
  private Instant lastUsedAt;

  protected ApiToken() {}

  public ApiToken(long userId, String tokenHash, String name) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.name = name;
  }

  public Long getId() {
    return id;
  }

  public long getUserId() {
    return userId;
  }

  public String getName() {
    return name;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public void setLastUsedAt(Instant lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }
}
