package io.github.nikpihu.chess.engine.fen.v1;

/** Thrown when a FEN string cannot be parsed. The message says which part is wrong. */
public final class FenException extends RuntimeException {

  public FenException(String fen, String problem) {
    super("Invalid FEN \"" + fen + "\": " + problem);
  }
}
