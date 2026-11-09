package com.gambyte.user.repo;

import com.gambyte.user.domain.EmailToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Database access for {@link EmailToken}. */
public interface EmailTokenRepository extends JpaRepository<EmailToken, String> {

  @Modifying
  @Query("delete from EmailToken t where t.expiresAt < current_timestamp")
  int deleteExpired();
}
