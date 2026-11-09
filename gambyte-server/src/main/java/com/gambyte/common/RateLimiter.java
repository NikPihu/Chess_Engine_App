package com.gambyte.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Token-bucket rate limiting (Bucket4j). Each (action, key) pair, e.g. ("login", client IP), gets
 * a bucket of {@code capacity} tokens refilled over {@code period}; each request takes one.
 *
 * <p>Buckets are kept in memory, which is right for one server. If Gambyte ever runs on several
 * servers, swap the map for Bucket4j's Redis integration so all servers share the buckets.
 */
@Component
public class RateLimiter {

  private final Cache<String, Bucket> buckets =
      Caffeine.newBuilder().maximumSize(100_000).expireAfterAccess(Duration.ofHours(1)).build();

  /**
   * Takes one token or throws HTTP 429.
   *
   * @param action what is limited ("login", "register", "move", ...)
   * @param key who is limited (client IP, user id, token)
   */
  public void check(String action, String key, int capacity, Duration period) {
    Bucket bucket =
        buckets.get(
            action + ':' + key,
            k ->
                Bucket.builder()
                    .addLimit(
                        Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build())
                    .build());
    if (!bucket.tryConsume(1)) {
      throw ApiException.tooManyRequests("Too many requests, slow down a little");
    }
  }
}
