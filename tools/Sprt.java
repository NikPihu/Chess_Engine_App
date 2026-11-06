package tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 5 testing lab: plays two engine versions against each other with fastchess until an
 * SPRT test decides, then records the result in docs/results/elo-ledger.md.
 *
 * <p>Run it straight from source (no build step needed for this file):
 *
 * <pre>
 *   java tools/Sprt.java v1 random
 *   java tools/Sprt.java v2 v1 --tc 5+0.05 --label "null move pruning"
 *   java tools/Sprt.java "v2:Features=all" "v2:Features=all,-nmp" --label "NMP"
 * </pre>
 *
 * <p>An engine spec is {@code name[:Option=value;Option=value]}: the name is our UCI "Engine"
 * option (any name in uci/Engines.java), the rest are extra UCI options.
 *
 * <p>Native engines (chess_engine_v3, C++): pass an {@code .exe} as {@code --jar} / {@code
 * --base-jar}. The exe is started directly, the spec's name is only a label, and the options after
 * ':' still apply:
 *
 * <pre>
 *   java tools/Sprt.java v3 nnue --jar chess_engine_v3/bin/engine_v3.exe --label "v3.0 port"
 * </pre>
 *
 * <p><b>SPRT</b> (sequential probability ratio test) answers "is NEW at least elo1 better, or at
 * most elo0?" and stops as soon as the evidence is strong enough (error rates alpha = beta = 5%).
 * H1 accepted = the change is an improvement; H0 accepted = it isn't (reject it). See
 * docs/learn/05-sprt-explained.md.
 */
public final class Sprt {

  private static final Path ROOT = Path.of("").toAbsolutePath();
  private static final Path FASTCHESS = ROOT.resolve("tools/bin/fastchess.exe");
  private static final Path BOOK = ROOT.resolve("tools/books/8moves_v3.pgn");
  private static final Path LEDGER = ROOT.resolve("docs/results/elo-ledger.md");

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.out.println(
          "usage: java tools/Sprt.java <new> <base> [--tc 8+0.08] [--concurrency N]"
              + " [--elo0 0] [--elo1 10] [--maxgames 6000] [--label text] [--jar path]"
              + " [--base-jar path] [--games N]");
      return;
    }
    String newSpec = args[0];
    String baseSpec = args[1];
    String tc = "8+0.08";
    int concurrency = Math.max(1, Runtime.getRuntime().availableProcessors() * 3 / 4);
    String elo0 = "0";
    String elo1 = "10";
    int maxGames = 6000;
    String label = newSpec + " vs " + baseSpec;
    // Default: the newest engine jar (v2 contains every engine: nnue, v2, v1, random).
    Path v2Jar = ROOT.resolve("chess_engine_v2/target/chess_engine_v2.jar");
    Path jar =
        Files.exists(v2Jar) ? v2Jar : ROOT.resolve("chess_engine_v1/target/chess_engine_v1.jar");
    Path baseJar = null;
    int fixedGames = 0; // > 0: plain match of that many games, no SPRT
    for (int i = 2; i + 1 < args.length; i += 2) {
      switch (args[i]) {
        case "--tc" -> tc = args[i + 1];
        case "--concurrency" -> concurrency = Integer.parseInt(args[i + 1]);
        case "--elo0" -> elo0 = args[i + 1];
        case "--elo1" -> elo1 = args[i + 1];
        case "--maxgames" -> maxGames = Integer.parseInt(args[i + 1]);
        case "--label" -> label = args[i + 1];
        case "--jar" -> jar = ROOT.resolve(args[i + 1]);
        case "--base-jar" -> baseJar = ROOT.resolve(args[i + 1]);
        case "--games" -> fixedGames = Integer.parseInt(args[i + 1]);
        default -> throw new IllegalArgumentException("unknown flag " + args[i]);
      }
    }
    require(FASTCHESS, "fastchess (see docs/phases/05-testing-lab.md)");
    require(BOOK, "the opening book (see docs/phases/05-testing-lab.md)");
    require(jar, jar + " - build it first: mvn -q package -DskipTests");
    if (baseJar == null) {
      baseJar = jar;
    }
    require(baseJar, baseJar + " - build it first: mvn -q package -DskipTests");

    // Snapshot the jar so rebuilding during a long test can't change the engines mid-match.
    String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    Path snapshot = ROOT.resolve("tools/versions/engine-" + stamp + extension(jar));
    Files.createDirectories(snapshot.getParent());
    Files.copy(jar, snapshot, StandardCopyOption.REPLACE_EXISTING);
    Path baseSnapshot = snapshot;
    if (!baseJar.equals(jar)) {
      baseSnapshot = ROOT.resolve("tools/versions/engine-" + stamp + "-base" + extension(baseJar));
      Files.copy(baseJar, baseSnapshot, StandardCopyOption.REPLACE_EXISTING);
    }
    Path results = ROOT.resolve("tools/results");
    Files.createDirectories(results);
    Path pgn = results.resolve(stamp + ".pgn");
    Path log = results.resolve(stamp + ".log");

    List<String> cmd = new ArrayList<>(List.of(FASTCHESS.toString()));
    cmd.addAll(engine(newSpec, "New", snapshot));
    cmd.addAll(engine(baseSpec, "Base", baseSnapshot));
    cmd.addAll(
        List.of(
            "-each", "tc=" + tc, "proto=uci",
            "-openings", "file=" + BOOK, "format=pgn", "order=random",
            "-games", "2", "-repeat",
            "-rounds", String.valueOf((fixedGames > 0 ? fixedGames : maxGames) / 2),
            "-concurrency", String.valueOf(concurrency),
            "-ratinginterval", "50",
            "-pgnout", "file=" + pgn));
    if (fixedGames <= 0) {
      cmd.addAll(
          List.of(
              "-sprt", "elo0=" + elo0, "elo1=" + elo1, "alpha=0.05", "beta=0.05",
              "model=normalized"));
      System.out.println("SPRT " + label + "  [" + elo0 + ", " + elo1 + "]  tc " + tc
          + "  concurrency " + concurrency);
    } else {
      elo0 = "-";
      elo1 = "-";
      System.out.println("Match " + label + ": " + fixedGames + " games, tc " + tc
          + "  concurrency " + concurrency);
    }
    System.out.println("log: " + log + "\n");

    Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    StringBuilder output = new StringBuilder();
    try (BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter logWriter = new PrintWriter(Files.newBufferedWriter(log))) {
      String line;
      while ((line = reader.readLine()) != null) {
        System.out.println(line);
        logWriter.println(line);
        output.append(line).append('\n');
      }
    }
    process.waitFor();
    appendToLedger(label, newSpec, baseSpec, tc, elo0, elo1, output.toString(), stamp);
  }

  /** fastchess arguments for one engine spec "name[:Opt=val;Opt=val]". */
  private static List<String> engine(String spec, String role, Path jar) {
    String[] parts = spec.split(":", 2);
    String name = parts[0];
    List<String> a = new ArrayList<>();
    a.add("-engine");
    boolean nativeExe = jar.toString().toLowerCase().endsWith(".exe");
    if (nativeExe) {
      a.add("cmd=" + jar);
    } else {
      a.add("cmd=java");
      a.add("args=-Xmx256m -XX:+UseSerialGC --add-modules jdk.incubator.vector -jar " + jar);
    }
    a.add("name=" + role + "-" + spec.replaceAll("[^A-Za-z0-9+-]", "_"));
    if (!nativeExe) {
      a.add("option.Engine=" + name);
    }
    a.add("option.Hash=16");
    if (parts.length > 1) {
      for (String option : parts[1].split(";")) {
        a.add("option." + option.trim());
      }
    }
    return a;
  }

  private static void appendToLedger(
      String label, String newSpec, String baseSpec, String tc, String elo0, String elo1,
      String output, String stamp) throws IOException {
    String elo = find(output, "\\bElo: (\\S+ \\+/- \\S+),");
    String nelo = find(output, "nElo: (\\S+ \\+/- \\S+)");
    String games = find(output, "Games: ([0-9]+)");
    String wins = find(output, "Wins: ([0-9]+)");
    String losses = find(output, "Losses: ([0-9]+)");
    String draws = find(output, "Draws: ([0-9]+)");
    String llr = find(output, "LLR: ([-0-9.]+)");
    String verdict =
        output.contains("H1 was accepted") ? "✅ H1 (better)"
            : output.contains("H0 was accepted") ? "❌ H0 (not better)"
            : elo0.equals("-") ? "📊 fixed match" : "⏸ inconclusive";

    Files.createDirectories(LEDGER.getParent());
    if (!Files.exists(LEDGER)) {
      Files.writeString(
          LEDGER,
          """
          # Elo ledger

          Every engine change is tested with SPRT (fastchess, 8moves_v3 openings, both colours)
          before it is accepted. Written automatically by `java tools/Sprt.java`.

          | Date | Change | New | Base | TC | Games | W-L-D | Elo | nElo | LLR | Bounds | Result | Log |
          |---|---|---|---|---|---|---|---|---|---|---|---|---|
          """);
    }
    String row =
        String.format(
            "| %s | %s | `%s` | `%s` | %s | %s | %s-%s-%s | %s | %s | %s | [%s, %s] | %s | %s |%n",
            stamp.substring(0, 8), label, newSpec, baseSpec, tc, games, wins, losses, draws,
            elo, nelo, llr, elo0, elo1, verdict, stamp);
    Files.writeString(LEDGER, row, StandardOpenOption.APPEND);
    System.out.println("\nLedger updated: " + verdict.substring(2) + "  (" + LEDGER + ")");
  }

  /** ".exe" for native engines, ".jar" otherwise (snapshots keep the right extension). */
  private static String extension(Path engine) {
    return engine.toString().toLowerCase().endsWith(".exe") ? ".exe" : ".jar";
  }

  /** Last match of the pattern's first group, or "?" (fastchess prints running totals). */
  private static String find(String text, String regex) {
    Matcher m = Pattern.compile(regex).matcher(text);
    String last = "?";
    while (m.find()) {
      last = m.group(1);
    }
    return last;
  }

  private static void require(Path path, String what) {
    if (!Files.exists(path)) {
      throw new IllegalStateException("Missing " + what + ": " + path);
    }
  }
}
