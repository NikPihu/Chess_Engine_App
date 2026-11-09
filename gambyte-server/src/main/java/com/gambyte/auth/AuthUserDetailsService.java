package com.gambyte.auth;

import com.gambyte.user.repo.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads accounts for login: by username or by email. Bot and system accounts cannot log in. */
@Service
public class AuthUserDetailsService implements UserDetailsService {

  private final UserRepository users;

  public AuthUserDetailsService(UserRepository users) {
    this.users = users;
  }

  @Override
  public UserDetails loadUserByUsername(String login) {
    return users
        .findByLogin(login)
        .filter(u -> u.getPasswordHash() != null)
        .map(AuthUser::of)
        .orElseThrow(() -> new UsernameNotFoundException("unknown account"));
  }
}
