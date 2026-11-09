package com.gambyte.user.repo;

import com.gambyte.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Database access for {@link User}. Lookups ignore case, matching the unique indexes. */
public interface UserRepository extends JpaRepository<User, Long> {

  @Query("select u from User u where lower(u.username) = lower(?1)")
  Optional<User> findByUsername(String username);

  @Query("select u from User u where lower(u.email) = lower(?1)")
  Optional<User> findByEmail(String email);

  /** Login accepts either the username or the email address. */
  @Query("select u from User u where lower(u.username) = lower(?1) or lower(u.email) = lower(?1)")
  Optional<User> findByLogin(String usernameOrEmail);

  @Query("select count(u) > 0 from User u where lower(u.username) = lower(?1)")
  boolean existsByUsername(String username);

  @Query("select count(u) > 0 from User u where lower(u.email) = lower(?1)")
  boolean existsByEmail(String email);

  /** Prefix search for the player search box (uses the lower(username) index). */
  @Query(
      "select u from User u where lower(u.username) like lower(concat(?1, '%')) and u.banned = false"
          + " order by u.username")
  List<User> searchByPrefix(String prefix, org.springframework.data.domain.Pageable page);
}
