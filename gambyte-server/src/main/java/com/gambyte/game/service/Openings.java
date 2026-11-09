package com.gambyte.game.service;

import java.util.List;
import java.util.Map;

/**
 * Names the opening of a game from its first moves: the longest known move sequence that the game
 * starts with wins. A compact hand-made table of the openings players meet most; to make it
 * complete, load the CC0 lichess-org/chess-openings TSV files into this map instead.
 */
public final class Openings {

  private Openings() {}

  /** SAN move sequence (space-separated) → opening name. */
  private static final Map<String, String> TABLE = Map.ofEntries(
      Map.entry("e4", "King's Pawn Game"),
      Map.entry("e4 e5", "Open Game"),
      Map.entry("e4 e5 Nf3 Nc6 Bb5", "Ruy Lopez"),
      Map.entry("e4 e5 Nf3 Nc6 Bb5 a6", "Ruy Lopez: Morphy Defense"),
      Map.entry("e4 e5 Nf3 Nc6 Bb5 Nf6", "Ruy Lopez: Berlin Defense"),
      Map.entry("e4 e5 Nf3 Nc6 Bc4", "Italian Game"),
      Map.entry("e4 e5 Nf3 Nc6 Bc4 Bc5", "Italian Game: Giuoco Piano"),
      Map.entry("e4 e5 Nf3 Nc6 Bc4 Nf6", "Italian Game: Two Knights Defense"),
      Map.entry("e4 e5 Nf3 Nc6 Bc4 Bc5 b4", "Italian Game: Evans Gambit"),
      Map.entry("e4 e5 Nf3 Nc6 d4", "Scotch Game"),
      Map.entry("e4 e5 Nf3 Nc6 Nc3 Nf6", "Four Knights Game"),
      Map.entry("e4 e5 Nf3 Nf6", "Petrov's Defense"),
      Map.entry("e4 e5 Nf3 d6", "Philidor Defense"),
      Map.entry("e4 e5 f4", "King's Gambit"),
      Map.entry("e4 e5 Nc3", "Vienna Game"),
      Map.entry("e4 e5 Bc4", "Bishop's Opening"),
      Map.entry("e4 e5 d4 exd4", "Center Game"),
      Map.entry("e4 c5", "Sicilian Defense"),
      Map.entry("e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6", "Sicilian Defense: Najdorf Variation"),
      Map.entry("e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6", "Sicilian Defense: Dragon Variation"),
      Map.entry("e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 Nf6 Nc3 e5", "Sicilian Defense: Sveshnikov Variation"),
      Map.entry("e4 c5 Nf3 e6", "Sicilian Defense: French Variation"),
      Map.entry("e4 c5 c3", "Sicilian Defense: Alapin Variation"),
      Map.entry("e4 c5 Nc3", "Sicilian Defense: Closed"),
      Map.entry("e4 c5 d4 cxd4 c3", "Sicilian Defense: Smith-Morra Gambit"),
      Map.entry("e4 e6", "French Defense"),
      Map.entry("e4 e6 d4 d5 e5", "French Defense: Advance Variation"),
      Map.entry("e4 e6 d4 d5 Nc3", "French Defense: Paulsen Variation"),
      Map.entry("e4 e6 d4 d5 Nd2", "French Defense: Tarrasch Variation"),
      Map.entry("e4 e6 d4 d5 exd5 exd5", "French Defense: Exchange Variation"),
      Map.entry("e4 c6", "Caro-Kann Defense"),
      Map.entry("e4 c6 d4 d5 e5", "Caro-Kann Defense: Advance Variation"),
      Map.entry("e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5", "Caro-Kann Defense: Classical Variation"),
      Map.entry("e4 d5", "Scandinavian Defense"),
      Map.entry("e4 d6", "Pirc Defense"),
      Map.entry("e4 g6", "Modern Defense"),
      Map.entry("e4 Nf6", "Alekhine Defense"),
      Map.entry("e4 Nc6", "Nimzowitsch Defense"),
      Map.entry("e4 b6", "Owen Defense"),
      Map.entry("d4", "Queen's Pawn Game"),
      Map.entry("d4 d5", "Queen's Pawn Game"),
      Map.entry("d4 d5 c4", "Queen's Gambit"),
      Map.entry("d4 d5 c4 e6", "Queen's Gambit Declined"),
      Map.entry("d4 d5 c4 dxc4", "Queen's Gambit Accepted"),
      Map.entry("d4 d5 c4 c6", "Slav Defense"),
      Map.entry("d4 d5 c4 e6 Nc3 Nf6 Nf3 c6", "Semi-Slav Defense"),
      Map.entry("d4 d5 c4 Nc6", "Chigorin Defense"),
      Map.entry("d4 d5 c4 e5", "Albin Countergambit"),
      Map.entry("d4 d5 Bf4", "London System"),
      Map.entry("d4 Nf6 Bf4", "London System"),
      Map.entry("d4 d5 Nf3 Nf6 Bf4", "London System"),
      Map.entry("d4 d5 e3", "Colle System"),
      Map.entry("d4 Nf6", "Indian Defense"),
      Map.entry("d4 Nf6 c4 e6 Nc3 Bb4", "Nimzo-Indian Defense"),
      Map.entry("d4 Nf6 c4 e6 Nf3 b6", "Queen's Indian Defense"),
      Map.entry("d4 Nf6 c4 g6 Nc3 Bg7", "King's Indian Defense"),
      Map.entry("d4 Nf6 c4 g6 Nc3 d5", "Grunfeld Defense"),
      Map.entry("d4 Nf6 c4 c5 d5 b5", "Benko Gambit"),
      Map.entry("d4 Nf6 c4 c5 d5 e6", "Modern Benoni"),
      Map.entry("d4 Nf6 c4 e6 g3", "Catalan Opening"),
      Map.entry("d4 Nf6 Bg5", "Trompowsky Attack"),
      Map.entry("d4 f5", "Dutch Defense"),
      Map.entry("d4 e5", "Englund Gambit"),
      Map.entry("c4", "English Opening"),
      Map.entry("c4 e5", "English Opening: King's English"),
      Map.entry("c4 c5", "English Opening: Symmetrical"),
      Map.entry("Nf3", "Zukertort Opening"),
      Map.entry("Nf3 d5 g3", "King's Indian Attack"),
      Map.entry("g3", "Hungarian Opening"),
      Map.entry("b3", "Nimzo-Larsen Attack"),
      Map.entry("f4", "Bird Opening"),
      Map.entry("e3", "Van't Kruijs Opening"),
      Map.entry("Nc3", "Van Geet Opening"),
      Map.entry("b4", "Polish Opening"),
      Map.entry("g4", "Grob Opening"));

  /** The name of the opening, or null for an unknown start. */
  public static String name(List<String> sans) {
    String best = null;
    StringBuilder prefix = new StringBuilder();
    for (int i = 0; i < Math.min(sans.size(), 12); i++) {
      if (i > 0) prefix.append(' ');
      prefix.append(sans.get(i).replace("+", "").replace("#", ""));
      String name = TABLE.get(prefix.toString());
      if (name != null) best = name;
    }
    return best;
  }
}
