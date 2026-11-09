package com.gambyte.user.service;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Who is online. Each connected client refreshes a Redis key with a 60-second time-to-live
 * (heartbeat); a user is online while the key exists. When a tab closes the heartbeats stop and
 * Redis deletes the key by itself, so no cleanup job is needed.
 */
@Service
public class PresenceService {

  private static final Duration TTL = Duration.ofSeconds(60);
  private final StringRedisTemplate redis;

  public PresenceService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public void heartbeat(long userId) {
    redis.opsForValue().set(key(userId), "1", TTL);
  }

  public void offline(long userId) {
    redis.delete(key(userId));
  }

  public boolean isOnline(long userId) {
    return Boolean.TRUE.equals(redis.hasKey(key(userId)));
  }

  /** Which of these users are online, in one Redis round trip. */
  public Set<Long> online(Collection<Long> userIds) {
    List<Long> ids = List.copyOf(userIds);
    Set<Long> out = new HashSet<>();
    if (ids.isEmpty()) return out;
    List<String> values =
        redis.opsForValue().multiGet(ids.stream().map(PresenceService::key).toList());
    for (int i = 0; values != null && i < ids.size(); i++) {
      if (values.get(i) != null) out.add(ids.get(i));
    }
    return out;
  }

  private static String key(long userId) {
    return "gambyte:presence:" + userId;
  }
}
