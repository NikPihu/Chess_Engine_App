package io.github.nikpihu.chess.uci;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UciSessionTest {

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
}
