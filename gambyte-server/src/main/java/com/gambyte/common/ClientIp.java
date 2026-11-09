package com.gambyte.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The client's IP address. Behind Caddy the real address arrives in X-Forwarded-For; Spring's
 * {@code server.forward-headers-strategy=framework} already rewrites {@code getRemoteAddr()}, so
 * this is just a readable name for it.
 */
public final class ClientIp {
  private ClientIp() {}

  public static String of(HttpServletRequest request) {
    return request.getRemoteAddr();
  }
}
