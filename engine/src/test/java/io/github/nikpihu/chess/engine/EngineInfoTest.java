package io.github.nikpihu.chess.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EngineInfoTest {

  @Test
  void fullNameContainsNameAndVersion() {
    assertThat(EngineInfo.fullName()).isEqualTo("ChessEngineApp 0.1.0");
  }
}
