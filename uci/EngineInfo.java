package uci;

/** Name and version reported by the engine (e.g. in reply to the UCI {@code uci} command). */
public final class EngineInfo {

  public static final String NAME = "ChessEngineApp";
  public static final String VERSION = "0.1.0";
  public static final String AUTHOR = "NikPihu";

  private EngineInfo() {}

  public static String fullName() {
    return NAME + " " + VERSION;
  }
}
