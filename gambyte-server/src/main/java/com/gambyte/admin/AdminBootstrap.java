package com.gambyte.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The first administrator: set {@code GAMBYTE_ADMIN_USERNAME=yourname}, register that account on
 * the site, and restart. The account becomes ADMIN; further staff are appointed from the admin
 * page. (There is deliberately no default admin password anywhere.)
 */
@Component
public class AdminBootstrap {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

  private final AdminController admin;
  private final String username;

  public AdminBootstrap(AdminController admin, @Value("${GAMBYTE_ADMIN_USERNAME:}") String username) {
    this.admin = admin;
    this.username = username;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void promote() {
    if (username == null || username.isBlank()) return;
    admin.promote(username);
    log.info("Ensured {} is an administrator", username);
  }
}
