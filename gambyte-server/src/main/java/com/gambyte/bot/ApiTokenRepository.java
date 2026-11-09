package com.gambyte.bot;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Database access for {@link ApiToken}. */
public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

  Optional<ApiToken> findByTokenHash(String tokenHash);

  List<ApiToken> findByUserIdOrderByCreatedAtDesc(long userId);
}
