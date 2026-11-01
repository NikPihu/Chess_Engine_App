package phase2;

import phase1.Bitboards;
import phase1.Board;
import phase1.CastlingRights;
import phase1.Colour;
import phase1.Move;
import phase1.MoveFlag;
import phase1.Piece;
import phase1.Square;

/**
 * Generates only <b>legal</b> moves (Lague's "second approach" from your Learnings 1, #8).
 *
 * <p>Instead of trying each move and checking whether our king is attacked afterwards, we work out
 * up front what is allowed:
 *
 * <ol>
 *   <li><b>King moves</b> go only to squares the enemy does not attack. Attacks are computed with
 *       our king removed from the board, so the king cannot "hide" behind itself on a checking ray.
 *   <li><b>Double check</b> → only the king may move.
 *   <li><b>Single check</b> → other pieces must capture the checker or block the ray: their targets
 *       are limited to the {@code checkMask}.
 *   <li><b>Pins</b> → a piece standing alone between our king and an enemy slider may only move
 *       along that line ({@link Lines#LINE}).
 *   <li><b>En passant</b> is checked separately: removing two pawns from one rank at once can
 *       expose the king sideways, which pin detection cannot see.
 *   <li><b>Castling</b> needs: the right, empty squares, rook in place, not in check, and the
 *       squares the king crosses not attacked.
 * </ol>
 *
 * With {@code capturesOnly} it generates just captures and queen promotions, which is what the
 * quiescence search in Phase 4 needs.
 */
public final class MoveGenerator {

  private MoveGenerator() {}

  public static void generate(Position pos, MoveList list) {
    generate(pos, list, false);
  }

  public static void generateCaptures(Position pos, MoveList list) {
    generate(pos, list, true);
  }

  private static void generate(Position pos, MoveList list, boolean capturesOnly) {
    list.clear();
    Board b = pos.board();
    int us = b.sideToMove();
    int them = Colour.opposite(us);
    long own = b.piecesOf(us);
    long enemy = b.piecesOf(them);
    long occupied = b.occupied();
    int king = b.kingSquare(us);

    // 1. King moves.
    long occupiedWithoutKing = occupied & ~Bitboards.of(king);
    long kingTargets = Attacks.KING[king] & ~own;
    if (capturesOnly) {
      kingTargets &= enemy;
    }
    while (kingTargets != 0) {
      int to = Bitboards.lowestSquare(kingTargets);
      kingTargets = Bitboards.withoutLowest(kingTargets);
      if (!pos.isAttacked(to, them, occupiedWithoutKing)) {
        list.add(Move.of(king, to));
      }
    }

    // 2./3. Checks.
    long checkers = pos.attackersTo(king, them, occupied);
    int checkCount = Bitboards.count(checkers);
    if (checkCount > 1) {
      return;
    }
    long checkMask = Bitboards.FULL;
    if (checkCount == 1) {
      int checker = Bitboards.lowestSquare(checkers);
      checkMask = checkers | Lines.BETWEEN[king][checker];
    }

    // 4. Pins: enemy sliders that would see our king if exactly one of our pieces moved away.
    long enemyRookLike =
        b.pieces(Piece.make(them, Piece.ROOK)) | b.pieces(Piece.make(them, Piece.QUEEN));
    long enemyBishopLike =
        b.pieces(Piece.make(them, Piece.BISHOP)) | b.pieces(Piece.make(them, Piece.QUEEN));
    long snipers =
        (Magics.rookAttacks(king, enemy) & enemyRookLike)
            | (Magics.bishopAttacks(king, enemy) & enemyBishopLike);
    long pinned = 0;
    while (snipers != 0) {
      int sniper = Bitboards.lowestSquare(snipers);
      snipers = Bitboards.withoutLowest(snipers);
      long blockers = Lines.BETWEEN[king][sniper] & occupied;
      if (Bitboards.count(blockers) == 1 && (blockers & own) != 0) {
        pinned |= blockers;
      }
    }

    long targetMask = checkMask & ~own;
    if (capturesOnly) {
      targetMask &= enemy;
    }

    // Knights (a pinned knight can never move: it always leaves the line).
    long knights = b.pieces(Piece.make(us, Piece.KNIGHT)) & ~pinned;
    while (knights != 0) {
      int from = Bitboards.lowestSquare(knights);
      knights = Bitboards.withoutLowest(knights);
      addAll(list, from, Attacks.KNIGHT[from] & targetMask);
    }

    // Sliders.
    long bishopLike =
        b.pieces(Piece.make(us, Piece.BISHOP)) | b.pieces(Piece.make(us, Piece.QUEEN));
    while (bishopLike != 0) {
      int from = Bitboards.lowestSquare(bishopLike);
      bishopLike = Bitboards.withoutLowest(bishopLike);
      long targets = Magics.bishopAttacks(from, occupied) & targetMask;
      if ((pinned & Bitboards.of(from)) != 0) {
        targets &= Lines.LINE[king][from];
      }
      addAll(list, from, targets);
    }
    long rookLike = b.pieces(Piece.make(us, Piece.ROOK)) | b.pieces(Piece.make(us, Piece.QUEEN));
    while (rookLike != 0) {
      int from = Bitboards.lowestSquare(rookLike);
      rookLike = Bitboards.withoutLowest(rookLike);
      long targets = Magics.rookAttacks(from, occupied) & targetMask;
      if ((pinned & Bitboards.of(from)) != 0) {
        targets &= Lines.LINE[king][from];
      }
      addAll(list, from, targets);
    }

    // Pawns.
    generatePawnMoves(pos, list, capturesOnly, us, king, enemy, occupied, checkMask, pinned);

    // 6. Castling.
    if (!capturesOnly && checkCount == 0) {
      generateCastling(pos, list, us, occupied);
    }
  }

  private static void generatePawnMoves(
      Position pos,
      MoveList list,
      boolean capturesOnly,
      int us,
      int king,
      long enemy,
      long occupied,
      long checkMask,
      long pinned) {
    Board b = pos.board();
    int forward = us == Colour.WHITE ? 8 : -8;
    int startRank = us == Colour.WHITE ? 1 : 6;
    int lastRank = us == Colour.WHITE ? 7 : 0;
    int epSquare = b.enPassantSquare();

    long pawns = b.pieces(Piece.make(us, Piece.PAWN));
    while (pawns != 0) {
      int from = Bitboards.lowestSquare(pawns);
      pawns = Bitboards.withoutLowest(pawns);
      long allowed = (pinned & Bitboards.of(from)) != 0 ? Lines.LINE[king][from] : Bitboards.FULL;
      long legal = checkMask & allowed;

      // Pushes.
      int one = from + forward;
      if ((occupied & Bitboards.of(one)) == 0) {
        if ((legal & Bitboards.of(one)) != 0) {
          if (Square.rank(one) == lastRank) {
            addPromotions(list, from, one, capturesOnly);
          } else if (!capturesOnly) {
            list.add(Move.of(from, one));
          }
        }
        int two = one + forward;
        if (!capturesOnly
            && Square.rank(from) == startRank
            && (occupied & Bitboards.of(two)) == 0
            && (legal & Bitboards.of(two)) != 0) {
          list.add(Move.of(from, two, MoveFlag.PAWN_DOUBLE_PUSH));
        }
      }

      // Captures.
      long captures = Attacks.PAWN[us][from] & enemy & legal;
      while (captures != 0) {
        int to = Bitboards.lowestSquare(captures);
        captures = Bitboards.withoutLowest(captures);
        if (Square.rank(to) == lastRank) {
          addPromotions(list, from, to, capturesOnly);
        } else {
          list.add(Move.of(from, to));
        }
      }

      // 5. En passant.
      if (epSquare != Square.NONE && (Attacks.PAWN[us][from] & Bitboards.of(epSquare)) != 0) {
        int capturedSquare = epSquare - forward;
        boolean resolvesCheck =
            (checkMask & (Bitboards.of(epSquare) | Bitboards.of(capturedSquare))) != 0;
        boolean followsPin = (allowed & Bitboards.of(epSquare)) != 0;
        if (resolvesCheck && followsPin && !enPassantExposesKing(pos, us, king, from, epSquare)) {
          list.add(Move.of(from, epSquare, MoveFlag.EN_PASSANT));
        }
      }
    }
  }

  /** After an en-passant capture, can an enemy slider see our king? (e.g. K..Pp..r on one rank) */
  private static boolean enPassantExposesKing(
      Position pos, int us, int king, int from, int epSquare) {
    Board b = pos.board();
    int them = Colour.opposite(us);
    int capturedSquare = epSquare - (us == Colour.WHITE ? 8 : -8);
    long occupiedAfter =
        (b.occupied() & ~Bitboards.of(from) & ~Bitboards.of(capturedSquare))
            | Bitboards.of(epSquare);
    long rookLike =
        b.pieces(Piece.make(them, Piece.ROOK)) | b.pieces(Piece.make(them, Piece.QUEEN));
    long bishopLike =
        b.pieces(Piece.make(them, Piece.BISHOP)) | b.pieces(Piece.make(them, Piece.QUEEN));
    return (Magics.rookAttacks(king, occupiedAfter) & rookLike) != 0
        || (Magics.bishopAttacks(king, occupiedAfter) & bishopLike) != 0;
  }

  private static void generateCastling(Position pos, MoveList list, int us, long occupied) {
    Board b = pos.board();
    int them = Colour.opposite(us);
    int rights = b.castlingRights();
    int rook = Piece.make(us, Piece.ROOK);
    int kingPiece = Piece.make(us, Piece.KING);
    if (us == Colour.WHITE) {
      if (b.pieceAt(Square.E1) != kingPiece) {
        return;
      }
      if (CastlingRights.has(rights, CastlingRights.WHITE_KING_SIDE)
          && b.pieceAt(Square.H1) == rook
          && (occupied & (Bitboards.of(Square.F1) | Bitboards.of(Square.G1))) == 0
          && !pos.isAttacked(Square.F1, them)
          && !pos.isAttacked(Square.G1, them)) {
        list.add(Move.of(Square.E1, Square.G1, MoveFlag.CASTLE));
      }
      if (CastlingRights.has(rights, CastlingRights.WHITE_QUEEN_SIDE)
          && b.pieceAt(Square.A1) == rook
          && (occupied
                  & (Bitboards.of(Square.B1) | Bitboards.of(Square.C1) | Bitboards.of(Square.D1)))
              == 0
          && !pos.isAttacked(Square.D1, them)
          && !pos.isAttacked(Square.C1, them)) {
        list.add(Move.of(Square.E1, Square.C1, MoveFlag.CASTLE));
      }
    } else {
      if (b.pieceAt(Square.E8) != kingPiece) {
        return;
      }
      if (CastlingRights.has(rights, CastlingRights.BLACK_KING_SIDE)
          && b.pieceAt(Square.H8) == rook
          && (occupied & (Bitboards.of(Square.F8) | Bitboards.of(Square.G8))) == 0
          && !pos.isAttacked(Square.F8, them)
          && !pos.isAttacked(Square.G8, them)) {
        list.add(Move.of(Square.E8, Square.G8, MoveFlag.CASTLE));
      }
      if (CastlingRights.has(rights, CastlingRights.BLACK_QUEEN_SIDE)
          && b.pieceAt(Square.A8) == rook
          && (occupied
                  & (Bitboards.of(Square.B8) | Bitboards.of(Square.C8) | Bitboards.of(Square.D8)))
              == 0
          && !pos.isAttacked(Square.D8, them)
          && !pos.isAttacked(Square.C8, them)) {
        list.add(Move.of(Square.E8, Square.C8, MoveFlag.CASTLE));
      }
    }
  }

  private static void addAll(MoveList list, int from, long targets) {
    while (targets != 0) {
      list.add(Move.of(from, Bitboards.lowestSquare(targets)));
      targets = Bitboards.withoutLowest(targets);
    }
  }

  private static void addPromotions(MoveList list, int from, int to, boolean queenOnly) {
    list.add(Move.of(from, to, MoveFlag.PROMOTE_QUEEN));
    if (!queenOnly) {
      list.add(Move.of(from, to, MoveFlag.PROMOTE_KNIGHT));
      list.add(Move.of(from, to, MoveFlag.PROMOTE_ROOK));
      list.add(Move.of(from, to, MoveFlag.PROMOTE_BISHOP));
    }
  }
}
