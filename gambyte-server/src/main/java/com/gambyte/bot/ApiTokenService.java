package com.gambyte.bot;

import com.gambyte.auth.AuthUser;
import com.gambyte.common.ApiException;
import com.gambyte.common.Tokens;
import com.gambyte.user.domain.User;
import com.gambyte.user.service.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates, lists, revokes and checks personal API tokens. */
@Service
public class ApiTokenService {

  /** Created tokens start with this, so they are easy to recognise (and to scan for in leaks). */
  public static final String PREFIX = "gbt_";

  private static final int MAX_TOKENS_PER_USER = 10;

  private final ApiTokenRepository tokens;
  private final UserService users;

  public ApiTokenService(ApiTokenRepository tokens, UserService users) {
    this.tokens = tokens;
    this.users = users;
  }

  /** What the user sees once, right after creating a token. */
  public record CreatedToken(long id, String name, String token) {}

  public record TokenView(long id, String name, Instant createdAt, Instant lastUsedAt) {}

  @Transactional
  public CreatedToken create(long userId, String name) {
    if (tokens.findByUserIdOrderByCreatedAtDesc(userId).size() >= MAX_TOKENS_PER_USER) {
      throw ApiException.badRequest("too_many_tokens", "Delete an old token first");
    }
    String token = PREFIX + Tokens.secret();
    ApiToken saved = tokens.save(new ApiToken(userId, Tokens.sha256(token), name));
    return new CreatedToken(saved.getId(), name, token);
  }

  public List<TokenView> list(long userId) {
    return tokens.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(t -> new TokenView(t.getId(), t.getName(), t.getCreatedAt(), t.getLastUsedAt()))
        .toList();
  }

  @Transactional
  public void revoke(long userId, long tokenId) {
    tokens
        .findById(tokenId)
        .filter(t -> t.getUserId() == userId)
        .ifPresent(tokens::delete);
  }

  /**
   * The account a bearer token belongs to, or empty if the token is unknown or the account is
   * banned. "Last used" is updated at most once a minute to avoid a write per request.
   */
  @Transactional
  public Optional<AuthUser> authenticate(String token) {
    if (token == null || !token.startsWith(PREFIX)) return Optional.empty();
    return tokens
        .findByTokenHash(Tokens.sha256(token))
        .flatMap(
            t -> {
              Instant now = Instant.now();
              if (t.getLastUsedAt() == null
                  || t.getLastUsedAt().isBefore(now.minus(Duration.ofMinutes(1)))) {
                t.setLastUsedAt(now);
              }
              User u = users.get(t.getUserId());
              return u.isBanned() ? Optional.empty() : Optional.of(AuthUser.of(u).withoutPassword());
            });
  }
}
