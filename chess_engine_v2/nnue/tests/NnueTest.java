package nnue.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import nnue.Accumulator;
import nnue.Features;
import nnue.NetFile;
import nnue.Network;
import org.junit.jupiter.api.Test;
import phase1.Colour;
import phase1.FenParser;
import phase1.Piece;
import phase1.Square;
import phase2.MoveGenerator;
import phase2.MoveList;
import phase2.Position;

class NnueTest {

  private static final Network NET = Network.random(1);

  private static final String[] STARTS = {
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
    "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
    "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
  };

  @Test
  void featureIndexes() {
    assertThat(Features.index(Colour.WHITE, Piece.WHITE_PAWN, Square.A1)).isEqualTo(0);
    assertThat(Features.index(Colour.WHITE, Piece.BLACK_KING, Square.H8)).isEqualTo(767);
    // A white piece seen by black = "their piece" on the vertically flipped square.
    assertThat(Features.index(Colour.BLACK, Piece.WHITE_KNIGHT, Square.G1))
        .isEqualTo(384 + 64 + Square.G8);
    for (int f = 0; f < Features.COUNT; f++) {
      assertThat(Features.flip(Features.flip(f))).isEqualTo(f);
    }
  }

  /** The heart of NNUE: incremental updates must give exactly the from-scratch result. */
  @Test
  void incrementalUpdatesEqualFullRefreshThroughRandomGames() {
    Random random = new Random(3);
    MoveList moves = new MoveList();
    for (int game = 0; game < 200; game++) {
      Position pos = Position.fromFen(STARTS[game % STARTS.length]);
      Accumulator incremental = new Accumulator();
      incremental.refresh(NET, pos.board());
      for (int ply = 0; ply < 80; ply++) {
        MoveGenerator.generate(pos, moves);
        if (moves.size() == 0) {
          break;
        }
        int move = moves.get(random.nextInt(moves.size()));
        Accumulator next = new Accumulator();
        next.copyFrom(incremental);
        next.applyMove(NET, pos.board(), move);
        pos.makeMove(move);
        incremental = next;

        Accumulator fresh = new Accumulator();
        fresh.refresh(NET, pos.board());
        int side = pos.sideToMove();
        assertThat(incremental.evaluate(NET, side)).isEqualTo(fresh.evaluate(NET, side));
      }
    }
  }

  /** Flipping the board and swapping colours must give the same score for the side to move. */
  @Test
  void evaluationIsColourSymmetric() {
    String fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
    String mirrored = "r3k2r/pppbbppp/2n2q1P/1P2p3/3pn3/BN2PNP1/P1PPQPB1/R3K2R b KQkq - 0 1";
    assertThat(eval(mirrored)).isEqualTo(eval(fen));
  }

  private static int eval(String fen) {
    Position pos = Position.fromFen(fen);
    Accumulator a = new Accumulator();
    a.refresh(NET, pos.board());
    return a.evaluate(NET, pos.sideToMove());
  }

  @Test
  void netFileRoundTrip() {
    byte[] bytes = NetFile.write(NET);
    assertThat(bytes.length).isEqualTo(12 + 2 * (768 * 256 + 256 + 512) + 4);
    Network copy = NetFile.parse(bytes);
    Accumulator a = new Accumulator();
    a.refresh(copy, FenParser.parse(STARTS[1]));
    assertThat(a.evaluate(copy, Colour.WHITE)).isEqualTo(eval(STARTS[1]));
    assertThatThrownBy(() -> NetFile.parse(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}))
        .hasMessageContaining("magic");
  }
}
