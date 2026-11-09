package com.gambyte.auth;

import com.gambyte.user.domain.User;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The logged-in user as Spring Security sees it. It is stored in the session (in Redis), so it is
 * small and {@link Serializable}: only the id, name and role. Anything else is loaded fresh from
 * the database when needed, so changes (premium bought, settings saved) show immediately.
 *
 * <p>Controllers receive it with {@code @AuthenticationPrincipal AuthUser me}.
 */
public record AuthUser(long id, String username, String passwordHash, String role, boolean banned)
    implements UserDetails, Serializable {

  public static AuthUser of(User u) {
    return new AuthUser(
        u.getId(), u.getUsername(), u.getPasswordHash(), u.getRole().name(), u.isBanned());
  }

  /** ROLE_USER plus ROLE_MOD / ROLE_ADMIN for staff (an admin is also a moderator). */
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return switch (role) {
      case "ADMIN" ->
          List.of(
              new SimpleGrantedAuthority("ROLE_USER"),
              new SimpleGrantedAuthority("ROLE_MOD"),
              new SimpleGrantedAuthority("ROLE_ADMIN"));
      case "MOD" ->
          List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_MOD"));
      default -> List.of(new SimpleGrantedAuthority("ROLE_USER"));
    };
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public boolean isAccountNonLocked() {
    return !banned;
  }

  /** The password hash must not travel with the session once login has been checked. */
  public AuthUser withoutPassword() {
    return new AuthUser(id, username, null, role, banned);
  }
}
