package uci.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import phase1.FenParser;
import phase1.Piece;
import phase1.Square;
import uci.UciSession;

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
  void positionWithMovesPlaysThem() {
    session.handle("position fen " + KIWIPETE + " moves e2a6 b4c3");
    assertThat(session.board().pieceAt(Square.A6)).isEqualTo(Piece.WHITE_BISHOP);
    assertThat(session.board().pieceAt(Square.C3)).isEqualTo(Piece.BLACK_PAWN);
  }

  @Test
  void positionStartposWithCastlingAndEnPassant() {
    session.handle(
        "position startpos moves e2e4 a7a6 e4e5 d7d5 e5d6 a6a5 g1f3 a5a4 f1e2 a4a3 e1g1");
    session.handle("fen");
    assertThat(output()).isEqualTo("rnbqkbnr/1pp1pppp/3P4/8/8/p4N2/PPPPBPPP/RNBQ1RK1 b kq - 1 6\n");
  }

  @Test
  void illegalMoveIsRejectedAndPositionKept() {
    session.handle("position startpos moves e2e5");
    assertThat(output()).contains("illegal move 'e2e5'");
    assertThat(session.board()).isEqualTo(FenParser.parse(FenParser.START_POSITION));
  }

  @Test
  void goPerftDividesByFirstMove() {
    session.handle("go perft 3");
    assertThat(output()).contains("e2e4: 600").contains("Nodes searched: 8902");
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
