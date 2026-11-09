package com.gambyte.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the {@code gambyte.*} block in {@code application.yml}.
 *
 * <p>Using one immutable record per settings group (instead of scattered {@code @Value}
 * annotations) means every setting the app reads is listed in one place.
 */
@ConfigurationProperties(prefix = "gambyte")
public record GambyteProperties(
    String siteUrl, Engine engine, Mail mail, Coach coach, Payment payment, Limits limits) {

  /** The C++ engine processes used for computer opponents, analysis and fair-play checks. */
  public record Engine(
      String path,
      String evalFile,
      String syzygyPath,
      int poolSize,
      int threads,
      int hashMb,
      long borrowTimeoutMs) {}

  /** Outgoing email. */
  public record Mail(String from) {}

  /** Optional AI chat coach (Claude API). Off when the key is empty. */
  public record Coach(String anthropicApiKey, String model, int maxTokens) {
    public boolean enabled() {
      return anthropicApiKey != null && !anthropicApiKey.isBlank();
    }
  }

  /** Payment provider credentials. */
  public record Payment(Razorpay razorpay) {}

  /** Razorpay credentials and the ids of the subscription plans created in its dashboard. */
  public record Razorpay(
      String keyId, String keySecret, String webhookSecret, String planMonthly, String planYearly) {
    public boolean enabled() {
      return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }
  }

  /** What free accounts may do per day (premium accounts are unlimited). */
  public record Limits(int freeReviewsPerDay, int freePuzzlesPerDay) {}
}
