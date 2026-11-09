package com.gambyte.bot;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates API calls that carry {@code Authorization: Bearer gbt_...} (bots and scripts). The
 * request is then handled exactly as if that user were logged in, but without a session.
 *
 * <p>Requests without the header pass through untouched (normal cookie login applies).
 */
@Component
public class BotTokenFilter extends OncePerRequestFilter {

  private final ApiTokenService tokens;

  public BotTokenFilter(ApiTokenService tokens) {
    this.tokens = tokens;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      tokens
          .authenticate(header.substring(7).trim())
          .ifPresent(
              user ->
                  SecurityContextHolder.getContext()
                      .setAuthentication(
                          new UsernamePasswordAuthenticationToken(
                              user, null, user.getAuthorities())));
    }
    chain.doFilter(request, response);
  }
}
