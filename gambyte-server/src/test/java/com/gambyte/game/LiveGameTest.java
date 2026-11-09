package com.gambyte.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gambyte.common.ApiException;
import com.gambyte.game.domain.GameStatus;
import com.gambyte.game.live.LiveGame;
import org.junit.jupiter.api.Test;
import phase1.FenParser;

class LiveGameTest {

  private static LiveGame game(long initialMs) {
    var white = new LiveGame.Player(1, "alice", null, 1500, true, null);
    var black = new LiveGame.Player(2, "bob", null, 1500, true, null);
    return new LiveGame("g1", white, black, "blitz", true, "lobby", null, FenParser.START_POSITION, initialMs, 0, 0);
  }

  @Test
  void foolsMateEndsTheGame() {
    LiveGame g = game(180_000);
    g.move(0, "f2f3", 1_000, 0);
    g.move(1, "e7e5", 2_000, 0);
    g.move(0, "g2g4", 3_000, 0);
    LiveGame.MoveResult r = g.move(1, "d8h4", 4_000, 0);
    assertThat(r.san()).isEqualTo("Qh4#");
    assertThat(g.status()).isEqualTo(GameStatus.MATE);
    assertThat(g.winner()).isEqualTo("black");
  }

  @Test
  void rejectsIllegalMovesAndWrongTurns() {
    LiveGame g = game(180_000);
    assertThatThrownBy(() -> g.move(1, "e7e5", 1_000, 0)).isInstanceOf(ApiException.class).hasMessageContaining("turn");
    assertThatThrownBy(() -> g.move(0, "e2e5", 1_000, 0)).isInstanceOf(ApiException.class).hasMessageContaining("Illegal");
    assertThat(g.moves()).isEmpty();
  }

  @Test
  void abortOnlyAtTheStart() {
    LiveGame g = game(180_000);
    g.move(0, "e2e4", 1_000, 0);
    g.move(1, "e7e5", 2_000, 0);
    assertThatThrownBy(() -> g.abort(0)).isInstanceOf(ApiException.class);
    g.resign(0);
    assertThat(g.status()).isEqualTo(GameStatus.RESIGN);
    assertThat(g.winner()).isEqualTo("black");
  }

  @Test
  void drawByAgreementNeedsBothSides() {
    LiveGame g = game(180_000);
    g.offerDraw(0);
    assertThat(g.status()).isEqualTo(GameStatus.STARTED);
    assertThat(g.drawOfferBy()).isEqualTo(0);
    g.offerDraw(1);
    assertThat(g.status()).isEqualTo(GameStatus.DRAW);
  }

  @Test
  void clocksStartOnlyAfterBothFirstMoves() {
    LiveGame g = game(60_000);
    g.move(0, "e2e4", 10_000, 0);
    assertThat(g.clock().running()).isEqualTo(-1);
    g.move(1, "e7e5", 20_000, 0);
    assertThat(g.clock().running()).isEqualTo(0); // now white's clock runs
  }

  @Test
  void losingOnTimeAgainstALoneKingIsADraw() {
    var white = new LiveGame.Player(1, "a", null, 1500, false, null);
    var black = new LiveGame.Player(2, "b", null, 1500, false, null);
    LiveGame g = new LiveGame("g2", white, black, "blitz", true, "lobby", null, "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1", 60_000, 0, 0);
    g.flag(0); // white flags; black has only a king
    assertThat(g.status()).isEqualTo(GameStatus.TIMEOUT);
    assertThat(g.winner()).isNull();
  }

  @Test
  void firstMoveTimeoutAbortsTheGame() {
    LiveGame g = game(180_000);
    assertThat(g.checkFirstMoveTimeout(LiveGame.FIRST_MOVE_MS - 1)).isFalse();
    assertThat(g.checkFirstMoveTimeout(LiveGame.FIRST_MOVE_MS + 1)).isTrue();
    assertThat(g.status()).isEqualTo(GameStatus.ABORTED);
  }
}
