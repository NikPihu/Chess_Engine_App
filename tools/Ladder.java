package tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 6 "strength sprints", automated: adds the v2 features one at a time and keeps each one
 * only if an SPRT match proves it makes the engine stronger.
 *
 * <pre>
 *   java tools/Ladder.java [--tc 3+0.03] [--maxgames 3000]
 * </pre>
 *
 * Step k plays "accepted features + feature k" against "accepted features". H1 → the feature is
 * kept; H0 or no decision → it is dropped. Every match lands in docs/results/elo-ledger.md, and the
 * final list is written to docs/results/v2-features.txt. Finally the finished v2 plays v1.
 */
public final class Ladder {

  private static final String[][] STEPS = {
    {"killers,history", "killer moves + history heuristic"},
    {"pvs", "principal variation search"},
    {"nmp", "null-move pruning"},
    {"lmr", "late move reductions"},
    {"checkext", "check extension"},
    {"aspiration", "aspiration windows"},
    {"rfp", "reverse futility pruning"},
    {"passed", "eval: passed pawns"},
    {"pawns", "eval: isolated + doubled pawns"},
    {"kingsafety", "eval: king safety"},
    {"mobility", "eval: mobility"},
    {"bishoppair", "eval: bishop pair"},
    {"mopup", "eval: mop-up (drive king to edge)"},
  };

  public static void main(String[] args) throws Exception {
    String tc = "3+0.03";
    String maxGames = "3000";
    for (int i = 0; i + 1 < args.length; i += 2) {
      switch (args[i]) {
        case "--tc" -> tc = args[i + 1];
        case "--maxgames" -> maxGames = args[i + 1];
        default -> throw new IllegalArgumentException("unknown flag " + args[i]);
      }
    }
    Path ledger = Path.of("docs/results/elo-ledger.md");
    // One fixed engine build for the whole ladder, even if the engine is rebuilt meanwhile.
    Path jar = Path.of("tools/versions/ladder-engine.jar");
    Files.createDirectories(jar.getParent());
    Files.copy(
        Path.of("chess_engine_v1/target/chess_engine_v1.jar"), jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    List<String> accepted = new ArrayList<>();

    for (String[] step : STEPS) {
      String base = accepted.isEmpty() ? "none" : String.join(",", accepted);
      String candidate = accepted.isEmpty() ? step[0] : base + "," + step[0];
      String label = "+ " + step[1];
      System.out.println("\n=== Ladder step: " + label + " ===");
      int code =
          run(
              "java", "tools/Sprt.java",
              "v2:Features=" + candidate, "v2:Features=" + base,
              "--tc", tc, "--maxgames", maxGames, "--label", label, "--jar", jar.toString());
      String verdict = lastLedgerLine(ledger);
      boolean keep = code == 0 && verdict.contains("H1");
      if (keep) {
        for (String f : step[0].split(",")) {
          accepted.add(f);
        }
      }
      System.out.println("=== " + label + ": " + (keep ? "KEPT" : "DROPPED") + " ===");
      Files.writeString(
          Path.of("docs/results/v2-features.txt"),
          (accepted.isEmpty() ? "none" : String.join(",", accepted)) + "\n",
          StandardCharsets.UTF_8);
    }

    String finalFeatures = accepted.isEmpty() ? "none" : String.join(",", accepted);
    System.out.println("\n=== Final v2 features: " + finalFeatures + " ===");
    run(
        "java", "tools/Sprt.java", "v2:Features=" + finalFeatures, "v1",
        "--tc", tc, "--maxgames", maxGames, "--label", "engine-v2.0 vs engine-v1.0",
        "--jar", jar.toString());
  }

  private static int run(String... cmd) throws Exception {
    return new ProcessBuilder(cmd).inheritIO().start().waitFor();
  }

  private static String lastLedgerLine(Path ledger) throws Exception {
    List<String> lines = Files.readAllLines(ledger, StandardCharsets.UTF_8);
    return lines.isEmpty() ? "" : lines.get(lines.size() - 1);
  }
}
