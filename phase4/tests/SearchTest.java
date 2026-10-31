package phase4.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import phase1.Move;
import phase2.MoveGenerator;
import phase2.MoveList;
import phase2.Position;
import phase3.Game;
import phase3.GameResult;
import phase3.RandomPlayer;
import phase3.SearchContext;
import phase3.SearchLimits;
import phase4.Search;

class SearchTest {

  /** Runs a search and returns the info lines it printed plus the best move (last element). */
  private static List<String> search(String fen, SearchLimits limits) {
    List<String> lines = new ArrayList<>();
    Position pos = Position.fromFen(fen);
    String before = pos.toFen();
    int best = new Search().bestMove(pos, limits, new SearchContext(lines::add));
    assertThat(pos.toFen()).as("search must leave the position unchanged").isEqualTo(before);
    lines.add(Move.toUci(best));
    return lines;
  }

  private static String last(List<String> lines) {
    return lines.get(lines.size() - 1);
  }

  @Test
  void findsBackRankMateInOne() {
    List<String> out = search("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", SearchLimits.depth(4));
    assertThat(last(out)).isEqualTo("d1d8");
    assertThat(out.get(out.size() - 2)).contains("score mate 1");
  }

  @Test
  void findsMateInTwoAndProvesIt() {
    String fen = "7k/8/8/8/8/8/R7/1R4K1 w - - 0 1"; // Ra7 then Rb8#
    List<String> out = search(fen, SearchLimits.depth(5));
    assertThat(out.get(out.size() - 2)).contains("score mate 2");
    // Brute-force check: after our move, every defence allows mate in one.
    Position pos = Position.fromFen(fen);
    pos.makeMove(phase2.UciMoveParser.parse(pos, last(out)));
    assertThat(isForcedMateInOne(pos)).isTrue();
  }

  /** Side to move (the defender) loses to a mate in one whatever it plays. */
  private static boolean isForcedMateInOne(Position pos) {
    MoveList defences = new MoveList();
    MoveGenerator.generate(pos, defences);
    if (defences.size() == 0) {
      return pos.inCheck();
    }
    for (int i = 0; i < defences.size(); i++) {
      pos.makeMove(defences.get(i));
      boolean mated = false;
      MoveList attacks = new MoveList();
      MoveGenerator.generate(pos, attacks);
      for (int j = 0; j < attacks.size() && !mated; j++) {
        pos.makeMove(attacks.get(j));
        MoveList replies = new MoveList();
        MoveGenerator.generate(pos, replies);
        mated = replies.size() == 0 && pos.inCheck();
        pos.unmakeMove(attacks.get(j));
      }
      pos.unmakeMove(defences.get(i));
      if (!mated) {
        return false;
      }
    }
    return true;
  }

  @Test
  void winsAFreeQueen() {
    assertThat(last(search("4k3/8/8/3q4/8/8/8/3QK3 w - - 0 1", SearchLimits.depth(3))))
        .isEqualTo("d1d5");
  }

  @Test
  void doesNotTakeAPoisonedPawn() {
    // Qxb7?? loses the queen to ...Rxb7 (Rb8 defends). Quiescence must see the recapture.
    String best = last(search("1r2k3/1p6/8/8/8/8/8/1Q2K3 w - - 0 1", SearchLimits.depth(2)));
    assertThat(best).isNotEqualTo("b1b7");
  }

  @Test
  void respectsMoveTime() {
    long start = System.currentTimeMillis();
    search(
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
        SearchLimits.moveTime(300));
    assertThat(System.currentTimeMillis() - start).isLessThan(1000);
  }

  @Test
  void stopsWhenAsked() throws Exception {
    Position pos = Position.startPosition();
    SearchLimits infinite = new SearchLimits();
    infinite.infinite = true;
    SearchContext ctx = SearchContext.silent();
    int[] result = new int[1];
    Thread t = new Thread(() -> result[0] = new Search().bestMove(pos, infinite, ctx));
    t.start();
    Thread.sleep(200);
    ctx.stop();
    t.join(2000);
    assertThat(t.isAlive()).isFalse();
    assertThat(result[0]).isNotEqualTo(Move.NULL);
  }

  @Test
  void beatsTheRandomPlayer() {
    Random random = new Random(11);
    int wins = 0;
    for (int g = 0; g < 4; g++) {
      Game game = new Game();
      Search v1 = new Search();
      RandomPlayer rnd = new RandomPlayer(random);
      boolean v1IsWhite = g % 2 == 0;
      while (!game.result().isOver() && game.moves().size() < 300) {
        boolean whiteToMove = game.position().sideToMove() == 0;
        int move =
            whiteToMove == v1IsWhite
                ? v1.bestMove(game.copyOfPosition(), SearchLimits.depth(3), SearchContext.silent())
                : rnd.bestMove(game.copyOfPosition(), null, SearchContext.silent());
        game.play(move);
      }
      GameResult r = game.result();
      assertThat(r)
          .as("v1 must never lose to random")
          .isNotEqualTo(v1IsWhite ? GameResult.BLACK_WINS : GameResult.WHITE_WINS);
      if (r == (v1IsWhite ? GameResult.WHITE_WINS : GameResult.BLACK_WINS)) {
        wins++;
      }
    }
    assertThat(wins).isGreaterThanOrEqualTo(3);
  }
}
