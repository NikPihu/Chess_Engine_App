package phase2;

import java.util.Arrays;
import phase1.Board;
import phase1.CastlingRights;
import phase1.Colour;
import phase1.FenParser;
import phase1.FenWriter;
import phase1.Move;
import phase1.MoveFlag;
import phase1.Piece;
import phase1.Square;

/**
 * A {@link Board} that can play moves and take them back.
 *
 * <p><b>Make / unmake.</b> The search plays millions of moves and undoes them again. Copying the
 * whole board each time would be slow, so {@link #makeMove} changes the board in place and pushes
 * the few things a move destroys (captured piece, old castling rights, old en-passant square, old
 * halfmove clock) onto an undo stack. {@link #unmakeMove} pops them and puts everything back.
 *
 * <p><b>Key history.</b> Before every move the current Zobrist key is saved. Phase 3 uses this list
 * to detect repetitions (the same key appearing again).
 */
public final class Position {

  /**
   * CASTLING_KEEP[sq]: rights that survive a move from or to sq (moving a king/rook loses them).
   */
  private static final int[] CASTLING_KEEP = new int[64];

  static {
    Arrays.fill(CASTLING_KEEP, CastlingRights.ALL);
    CASTLING_KEEP[Square.A1] &= ~CastlingRights.WHITE_QUEEN_SIDE;
    CASTLING_KEEP[Square.H1] &= ~CastlingRights.WHITE_KING_SIDE;
    CASTLING_KEEP[Square.E1] &= ~(CastlingRights.WHITE_KING_SIDE | CastlingRights.WHITE_QUEEN_SIDE);
    CASTLING_KEEP[Square.A8] &= ~CastlingRights.BLACK_QUEEN_SIDE;
    CASTLING_KEEP[Square.H8] &= ~CastlingRights.BLACK_KING_SIDE;
    CASTLING_KEEP[Square.E8] &= ~(CastlingRights.BLACK_KING_SIDE | CastlingRights.BLACK_QUEEN_SIDE);
  }

  private final Board board;

  // Undo stack: one packed int per move played, plus the key before that move.
  private int[] undo = new int[512];
  private long[] keyHistory = new long[512];
  private int historySize = 0;

  public Position(Board board) {
    this.board = board;
  }

  public static Position fromFen(String fen) {
    return new Position(FenParser.parse(fen));
  }

  public static Position startPosition() {
    return fromFen(FenParser.START_POSITION);
  }

  /** The underlying board. Read it freely; change it only through makeMove / unmakeMove. */
  public Board board() {
    return board;
  }

  public int sideToMove() {
    return board.sideToMove();
  }

  public long key() {
    return board.zobristKey();
  }

  public String toFen() {
    return FenWriter.write(board);
  }

  /** Number of moves played on this Position (and so the length of the key history). */
  public int historySize() {
    return historySize;
  }

  /** Zobrist key of the position before move number {@code index} was played. */
  public long keyBefore(int index) {
    return keyHistory[index];
  }

  // ---------------------------------------------------------------- attacks

  /** Bitboard of {@code byColour}'s pieces attacking {@code sq}, given {@code occupied}. */
  public long attackersTo(int sq, int byColour, long occupied) {
    int them = byColour;
    long rookLike =
        board.pieces(Piece.make(them, Piece.ROOK)) | board.pieces(Piece.make(them, Piece.QUEEN));
    long bishopLike =
        board.pieces(Piece.make(them, Piece.BISHOP)) | board.pieces(Piece.make(them, Piece.QUEEN));
    // A pawn of colour C attacks sq exactly when a pawn of the OTHER colour on sq would attack it.
    return (Attacks.PAWN[Colour.opposite(them)][sq] & board.pieces(Piece.make(them, Piece.PAWN)))
        | (Attacks.KNIGHT[sq] & board.pieces(Piece.make(them, Piece.KNIGHT)))
        | (Attacks.KING[sq] & board.pieces(Piece.make(them, Piece.KING)))
        | (Magics.rookAttacks(sq, occupied) & rookLike)
        | (Magics.bishopAttacks(sq, occupied) & bishopLike);
  }

  public boolean isAttacked(int sq, int byColour, long occupied) {
    return attackersTo(sq, byColour, occupied) != 0;
  }

  public boolean isAttacked(int sq, int byColour) {
    return isAttacked(sq, byColour, board.occupied());
  }

  /** Enemy pieces currently giving check to the side to move. */
  public long checkers() {
    int us = board.sideToMove();
    return attackersTo(board.kingSquare(us), Colour.opposite(us), board.occupied());
  }

  public boolean inCheck() {
    return checkers() != 0;
  }

  // ---------------------------------------------------------------- make / unmake

  /** Plays a move (it must come from MoveGenerator, i.e. be legal in this position). */
  public void makeMove(int move) {
    int from = Move.start(move);
    int to = Move.target(move);
    int flag = Move.flag(move);
    int us = board.sideToMove();
    int piece = board.pieceAt(from);
    int captureSquare = flag == MoveFlag.EN_PASSANT ? to + (us == Colour.WHITE ? -8 : 8) : to;
    int captured = board.pieceAt(captureSquare);

    pushUndo(captured);

    if (captured != Piece.NONE) {
      board.removePiece(captureSquare);
    }
    board.removePiece(from);
    int placed = MoveFlag.isPromotion(flag) ? Piece.make(us, MoveFlag.promotionType(flag)) : piece;
    board.putPiece(placed, to);

    if (flag == MoveFlag.CASTLE) {
      moveCastlingRook(to, false);
    }

    board.setCastlingRights(board.castlingRights() & CASTLING_KEEP[from] & CASTLING_KEEP[to]);
    board.setEnPassantSquare(flag == MoveFlag.PAWN_DOUBLE_PUSH ? (from + to) / 2 : Square.NONE);
    boolean resetsClock = Piece.type(piece) == Piece.PAWN || captured != Piece.NONE;
    board.setHalfmoveClock(resetsClock ? 0 : board.halfmoveClock() + 1);
    if (us == Colour.BLACK) {
      board.setFullmoveNumber(board.fullmoveNumber() + 1);
    }
    board.setSideToMove(Colour.opposite(us));
  }

  /** Takes back the last move played with {@link #makeMove}. */
  public void unmakeMove(int move) {
    int from = Move.start(move);
    int to = Move.target(move);
    int flag = Move.flag(move);
    int us = Colour.opposite(board.sideToMove()); // the side that played the move

    int packed = undo[--historySize];
    int captured = packed & 0xF;

    board.setSideToMove(us);
    if (us == Colour.BLACK) {
      board.setFullmoveNumber(board.fullmoveNumber() - 1);
    }
    if (flag == MoveFlag.CASTLE) {
      moveCastlingRook(to, true);
    }
    int placed = board.removePiece(to);
    board.putPiece(MoveFlag.isPromotion(flag) ? Piece.make(us, Piece.PAWN) : placed, from);
    if (captured != Piece.NONE) {
      int captureSquare = flag == MoveFlag.EN_PASSANT ? to + (us == Colour.WHITE ? -8 : 8) : to;
      board.putPiece(captured, captureSquare);
    }
    restoreState(packed);
  }

  /** "Passes" the turn (used by null-move pruning in Phase 6). Never legal in a real game. */
  public void makeNullMove() {
    pushUndo(Piece.NONE);
    board.setEnPassantSquare(Square.NONE);
    board.setHalfmoveClock(board.halfmoveClock() + 1);
    board.setSideToMove(Colour.opposite(board.sideToMove()));
  }

  public void unmakeNullMove() {
    int packed = undo[--historySize];
    board.setSideToMove(Colour.opposite(board.sideToMove()));
    restoreState(packed);
  }

  /**
   * Packs what a move destroys into one int: captured piece (4 bits), castling rights (4), en-
   * passant square + 1 (7), halfmove clock (rest).
   */
  private void pushUndo(int captured) {
    if (historySize == undo.length) {
      undo = Arrays.copyOf(undo, undo.length * 2);
      keyHistory = Arrays.copyOf(keyHistory, keyHistory.length * 2);
    }
    keyHistory[historySize] = board.zobristKey();
    undo[historySize++] =
        captured
            | board.castlingRights() << 4
            | (board.enPassantSquare() + 1) << 8
            | board.halfmoveClock() << 15;
  }

  private void restoreState(int packed) {
    board.setCastlingRights((packed >>> 4) & 0xF);
    board.setEnPassantSquare(((packed >>> 8) & 0x7F) - 1);
    board.setHalfmoveClock(packed >>> 15);
  }

  /** Moves the rook for a castling move whose king lands on {@code kingTarget}. */
  private void moveCastlingRook(int kingTarget, boolean undoing) {
    int rookFrom;
    int rookTo;
    switch (kingTarget) {
      case Square.G1 -> {
        rookFrom = Square.H1;
        rookTo = Square.F1;
      }
      case Square.C1 -> {
        rookFrom = Square.A1;
        rookTo = Square.D1;
      }
      case Square.G8 -> {
        rookFrom = Square.H8;
        rookTo = Square.F8;
      }
      case Square.C8 -> {
        rookFrom = Square.A8;
        rookTo = Square.D8;
      }
      default -> throw new IllegalStateException("Bad castling target " + Square.name(kingTarget));
    }
    if (undoing) {
      board.putPiece(board.removePiece(rookTo), rookFrom);
    } else {
      board.putPiece(board.removePiece(rookFrom), rookTo);
    }
  }
}
