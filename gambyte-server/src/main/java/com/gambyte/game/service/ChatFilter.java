package com.gambyte.game.service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A small chat filter: masks common insults and strips links (links in chat are the classic way to
 * spread scams). It is deliberately simple; reports and mutes handle the rest.
 */
public final class ChatFilter {

  private static final List<Pattern> BAD =
      List.of("fuck", "shit", "bitch", "cunt", "nigg", "fag", "retard", "kys").stream()
          .map(w -> Pattern.compile("(?i)" + w + "\\w*"))
          .toList();

  private static final Pattern LINK = Pattern.compile("(?i)\\b(https?://|www\\.)\\S+");

  private ChatFilter() {}

  public static String clean(String text) {
    String out = LINK.matcher(text.strip()).replaceAll("[link removed]");
    for (Pattern p : BAD) out = p.matcher(out).replaceAll(m -> "*".repeat(m.group().length()));
    return out;
  }
}
