package com.gambyte.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Random identifiers and tokens. */
public final class Tokens {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final char[] BASE62 =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

  private Tokens() {}

  /** A short random id like Lichess game ids ("q7ZvsdUF"): 8 chars = 47 bits. */
  public static String shortId(int length) {
    char[] out = new char[length];
    for (int i = 0; i < length; i++) out[i] = BASE62[RANDOM.nextInt(BASE62.length)];
    return new String(out);
  }

  /** A secret token for links and API keys (256 bits, URL-safe). */
  public static String secret() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** SHA-256 hex of a token: what is stored in the database instead of the token itself. */
  public static String sha256(String token) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
