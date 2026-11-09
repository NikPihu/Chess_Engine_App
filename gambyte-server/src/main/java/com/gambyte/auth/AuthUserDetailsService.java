package com.gambyte.auth;

import com.gambyte.user.service.UserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads accounts for login: by username or by email. Bot and system accounts cannot log in. */
@Service
public class AuthUserDetailsService implements UserDetailsService {

  private final UserService users;

  public AuthUserDetailsService(UserService users) {
    this.users = users;
  }

  @Override
  public UserDetails loadUserByUsername(String login) {
    return users
        .findForLogin(login)
        .filter(u -> u.getPasswordHash() != null)
        .map(AuthUser::of)
        .orElseThrow(() -> new UsernameNotFoundException("unknown account"));
  }
}
