package phase6;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * On/off switches for every Phase 6 improvement, packed into one int.
 *
 * <p>Each feature can be switched off on its own, so the testing lab can prove that it helps:
 * {@code java tools/Sprt.java "v2:Features=killers,history" "v2:Features=killers"} plays the engine
 * with and without history ordering. Text forms: {@code all}, {@code none}, a comma list ({@code
 * pvs,nmp}), or {@code all,-nmp} (everything except null move).
 */
public final class Features {

  // Search
  public static final int KILLERS = 1;
  public static final int HISTORY = 1 << 1;
  public static final int PVS = 1 << 2;
  public static final int NULL_MOVE = 1 << 3;
  public static final int LMR = 1 << 4;
  public static final int CHECK_EXTENSION = 1 << 5;
  public static final int ASPIRATION = 1 << 6;
  public static final int REVERSE_FUTILITY = 1 << 7;
  // Evaluation
  public static final int PASSED_PAWNS = 1 << 8;
  public static final int PAWN_STRUCTURE = 1 << 9;
  public static final int KING_SAFETY = 1 << 10;
  public static final int MOBILITY = 1 << 11;
  public static final int BISHOP_PAIR = 1 << 12;
  public static final int MOP_UP = 1 << 13;

  public static final int NONE = 0;
  public static final int ALL = (1 << 14) - 1;

  /** Names used in the text form, in the order the features were added. */
  public static final Map<String, Integer> NAMES = new LinkedHashMap<>();

  static {
    NAMES.put("killers", KILLERS);
    NAMES.put("history", HISTORY);
    NAMES.put("pvs", PVS);
    NAMES.put("nmp", NULL_MOVE);
    NAMES.put("lmr", LMR);
    NAMES.put("checkext", CHECK_EXTENSION);
    NAMES.put("aspiration", ASPIRATION);
    NAMES.put("rfp", REVERSE_FUTILITY);
    NAMES.put("passed", PASSED_PAWNS);
    NAMES.put("pawns", PAWN_STRUCTURE);
    NAMES.put("kingsafety", KING_SAFETY);
    NAMES.put("mobility", MOBILITY);
    NAMES.put("bishoppair", BISHOP_PAIR);
    NAMES.put("mopup", MOP_UP);
  }

  private Features() {}

  public static boolean on(int features, int feature) {
    return (features & feature) != 0;
  }

  public static int parse(String text) {
    int result = NONE;
    for (String raw : text.toLowerCase().split(",")) {
      String word = raw.trim();
      if (word.isEmpty() || word.equals("none")) {
        continue;
      }
      boolean remove = word.startsWith("-");
      String name = remove ? word.substring(1) : word;
      int bits;
      if (name.equals("all")) {
        bits = ALL;
      } else if (NAMES.containsKey(name)) {
        bits = NAMES.get(name);
      } else {
        throw new IllegalArgumentException(
            "Unknown feature '" + name + "', known: " + NAMES.keySet());
      }
      result = remove ? result & ~bits : result | bits;
    }
    return result;
  }

  public static String toText(int features) {
    if (features == ALL) {
      return "all";
    }
    StringBuilder sb = new StringBuilder();
    NAMES.forEach(
        (name, bit) -> {
          if (on(features, bit)) {
            sb.append(sb.isEmpty() ? "" : ",").append(name);
          }
        });
    return sb.isEmpty() ? "none" : sb.toString();
  }
}
