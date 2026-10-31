package io.github.nikpihu.chess.uci;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nikpihu.chess.engine.board.v1.Piece;
import io.github.nikpihu.chess.engine.board.v1.Square;
import io.github.nikpihu.chess.engine.fen.v1.FenParser;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UciSessionTest {

  private static final String KIWIPETE =
      "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";

  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
  private final UciSession session =
      new UciSession(new PrintStream(buffer, true, StandardCharsets.UTF_8));

  private String output() {
    return buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
  }

  @Test
  void uciHandshake() {
    session.handle("uci");
    assertThat(output()).startsWith("id name ChessEngineApp").endsWith("uciok\n");
  }

  @Test
  void isReady() {
    session.handle("isready");
    assertThat(output()).isEqualTo("readyok\n");
  }

  @Test
  void quitEndsSession() {
    assertThat(session.handle("quit")).isFalse();
    assertThat(session.handle("isready")).isTrue();
  }

  @Test
  void startsInTheStartPosition() {
    session.handle("fen");
    assertThat(output()).isEqualTo(FenParser.START_POSITION + "\n");
  }

  @Test
  void positionFenThenD() {
    session.handle("position fen " + KIWIPETE);
    session.handle("d");
    assertThat(output())
        .contains(" 8 | r | . | . | . | k | . | . | r | 8")
        .contains(" 1 | R | . | . | . | K | . | . | R | 1")
        .contains("FEN:        " + KIWIPETE)
        .contains("Castling:   KQkq")
        .contains("Zobrist:    0x");
  }

  @Test
  void positionFenWithMovesKeepsBasePositionAndWarns() {
    session.handle("position fen " + KIWIPETE + " moves e2a6");
    assertThat(session.board().pieceAt(Square.E2)).isEqualTo(Piece.WHITE_BISHOP);
    assertThat(output()).contains("moves are ignored until Phase 2");
  }

  @Test
  void invalidFenKeepsPreviousPosition() {
    session.handle("position fen not/a/fen w - - 0 1");
    assertThat(output()).startsWith("info string error: Invalid FEN");
    assertThat(session.board()).isEqualTo(FenParser.parse(FenParser.START_POSITION));
  }

  @Test
  void bitboardOfWhitePawns() {
    session.handle("bb P");
    assertThat(output())
        .contains(" 2  1 1 1 1 1 1 1 1")
        .contains(" 3  . . . . . . . .")
        .contains("hex:   0x000000000000FF00")
        .contains("count: 8");
  }

  @Test
  void unknownBitboard() {
    session.handle("bb x");
    assertThat(output()).contains("unknown bitboard 'x'");
  }
}
