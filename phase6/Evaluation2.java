package phase6;

import phase1.Bitboards;
import phase1.Board;
import phase1.Colour;
import phase1.Piece;
import phase1.Square;
import phase2.Attacks;
import phase2.Magics;
import phase4.PestoTables;

/**
 * Evaluation v2 = PeSTO (v1) + the terms from your Learnings, each behind a {@link Features} switch
 * so the testing lab can prove it helps:
 *
 * <ul>
 *   <li><b>Passed pawns</b> (no enemy pawn can stop it): bonus growing as it advances.
 *   <li><b>Pawn structure</b>: isolated pawns (no friendly pawn on a neighbouring file) and doubled
 *       pawns are weaknesses.
 *   <li><b>King safety</b> (the term Lague skipped): pawn shield in front of the king, open files
 *       next to it, and how many enemy pieces attack the squares around it (penalty grows
 *       quadratically: two attackers are far more than twice as dangerous as one).
 *   <li><b>Mobility</b>: pieces with more safe squares are more active.
 *   <li><b>Bishop pair</b>: two bishops cover both square colours.
 *   <li><b>Mop-up</b> (Lague's endgame trick): when clearly winning, drive the enemy king to the
 *       edge and bring ours closer, so the engine actually delivers mate.
 * </ul>
 *
 * Every term is computed for white and black the same way and subtracted, with separate
 * middlegame/endgame values blended by game phase exactly like v1.
 */
public final class Evaluation2 {

  // Passed pawn bonus by relative rank (rank 2 .. rank 7), middlegame and endgame.
  private static final int[] PASSED_MG = {0, 5, 10, 15, 25, 40, 65, 0};
  private static final int[] PASSED_EG = {0, 10, 15, 30, 55, 90, 140, 0};
  private static final int ISOLATED_MG = -10;
  private static final int ISOLATED_EG = -15;
  private static final int DOUBLED_MG = -10;
  private static final int DOUBLED_EG = -20;
  private static final int BISHOP_PAIR_MG = 30;
  private static final int BISHOP_PAIR_EG = 50;

  /** Attack weight of each piece type on the king zone (none, P, N, B, R, Q, K). */
  private static final int[] KING_ATTACK_WEIGHT = {0, 0, 2, 2, 3, 5, 0};

  /** PASSED_MASK[colour][sq]: squares in front on the same and adjacent files. */
  private static final long[][] PASSED_MASK = new long[2][64];

  /** ADJACENT_FILES[file]: the neighbouring files. */
  private static final long[] ADJACENT_FILES = new long[8];

  static {
    for (int file = 0; file < 8; file++) {
      long adjacent = 0;
      if (file > 0) {
        adjacent |= Bitboards.FILES[file - 1];
      }
      if (file < 7) {
        adjacent |= Bitboards.FILES[file + 1];
      }
      ADJACENT_FILES[file] = adjacent;
    }
    for (int sq = 0; sq < 64; sq++) {
      long files = Bitboards.FILES[Square.file(sq)] | ADJACENT_FILES[Square.file(sq)];
      long whiteAhead = 0;
      long blackAhead = 0;
      for (int r = Square.rank(sq) + 1; r < 8; r++) {
        whiteAhead |= Bitboards.RANKS[r];
      }
      for (int r = Square.rank(sq) - 1; r >= 0; r--) {
        blackAhead |= Bitboards.RANKS[r];
      }
      PASSED_MASK[Colour.WHITE][sq] = files & whiteAhead;
      PASSED_MASK[Colour.BLACK][sq] = files & blackAhead;
    }
  }

  private Evaluation2() {}

  /** Score in centipawns from the side to move's point of view. */
  public static int evaluate(Board board, int features) {
    int[] mgEg = new int[2];
    int phase = 0;

    // v1: PeSTO material + piece-square tables.
    for (int piece : Piece.ALL) {
      long bb = board.pieces(piece);
      int sign = Piece.colour(piece) == Colour.WHITE ? 1 : -1;
      while (bb != 0) {
        int sq = Bitboards.lowestSquare(bb);
        bb = Bitboards.withoutLowest(bb);
        mgEg[0] += sign * PestoTables.MG[piece][sq];
        mgEg[1] += sign * PestoTables.EG[piece][sq];
        phase += PHASE[Piece.type(piece)];
      }
    }
    phase = Math.min(phase, 24);

    for (int colour = Colour.WHITE; colour <= Colour.BLACK; colour++) {
      int sign = colour == Colour.WHITE ? 1 : -1;
      if (Features.on(features, Features.PASSED_PAWNS)
          || Features.on(features, Features.PAWN_STRUCTURE)) {
        pawns(board, colour, sign, features, mgEg);
      }
      if (Features.on(features, Features.MOBILITY)) {
        mobility(board, colour, sign, mgEg);
      }
      if (Features.on(features, Features.KING_SAFETY)) {
        kingSafety(board, colour, sign, mgEg);
      }
      if (Features.on(features, Features.BISHOP_PAIR)
          && Bitboards.count(board.pieces(Piece.make(colour, Piece.BISHOP))) >= 2) {
        mgEg[0] += sign * BISHOP_PAIR_MG;
        mgEg[1] += sign * BISHOP_PAIR_EG;
      }
    }

    int white = (mgEg[0] * phase + mgEg[1] * (24 - phase)) / 24;
    if (Features.on(features, Features.MOP_UP)) {
      white += mopUp(board, white, phase);
    }
    return board.sideToMove() == Colour.WHITE ? white : -white;
  }

  private static final int[] PHASE = {0, 0, 1, 1, 2, 4, 0};

  private static void pawns(Board board, int colour, int sign, int features, int[] mgEg) {
    long ours = board.pieces(Piece.make(colour, Piece.PAWN));
    long theirs = board.pieces(Piece.make(Colour.opposite(colour), Piece.PAWN));
    long bb = ours;
    while (bb != 0) {
      int sq = Bitboards.lowestSquare(bb);
      bb = Bitboards.withoutLowest(bb);
      int file = Square.file(sq);
      if (Features.on(features, Features.PASSED_PAWNS) && (PASSED_MASK[colour][sq] & theirs) == 0) {
        int relativeRank = colour == Colour.WHITE ? Square.rank(sq) : 7 - Square.rank(sq);
        mgEg[0] += sign * PASSED_MG[relativeRank];
        mgEg[1] += sign * PASSED_EG[relativeRank];
      }
      if (Features.on(features, Features.PAWN_STRUCTURE) && (ADJACENT_FILES[file] & ours) == 0) {
        mgEg[0] += sign * ISOLATED_MG;
        mgEg[1] += sign * ISOLATED_EG;
      }
    }
    if (Features.on(features, Features.PAWN_STRUCTURE)) {
      for (int file = 0; file < 8; file++) {
        int count = Bitboards.count(ours & Bitboards.FILES[file]);
        if (count > 1) {
          mgEg[0] += sign * DOUBLED_MG * (count - 1);
          mgEg[1] += sign * DOUBLED_EG * (count - 1);
        }
      }
    }
  }

  private static void mobility(Board board, int colour, int sign, int[] mgEg) {
    int them = Colour.opposite(colour);
    long own = board.piecesOf(colour);
    long occupied = board.occupied();
    // Squares attacked by enemy pawns are not "safe" mobility.
    long enemyPawnAttacks = 0;
    long enemyPawns = board.pieces(Piece.make(them, Piece.PAWN));
    while (enemyPawns != 0) {
      enemyPawnAttacks |= Attacks.PAWN[them][Bitboards.lowestSquare(enemyPawns)];
      enemyPawns = Bitboards.withoutLowest(enemyPawns);
    }
    long safe = ~own & ~enemyPawnAttacks;

    long knights = board.pieces(Piece.make(colour, Piece.KNIGHT));
    while (knights != 0) {
      int n = Bitboards.count(Attacks.KNIGHT[Bitboards.lowestSquare(knights)] & safe);
      knights = Bitboards.withoutLowest(knights);
      mgEg[0] += sign * (n - 4) * 4;
      mgEg[1] += sign * (n - 4) * 4;
    }
    long bishops = board.pieces(Piece.make(colour, Piece.BISHOP));
    while (bishops != 0) {
      int n =
          Bitboards.count(Magics.bishopAttacks(Bitboards.lowestSquare(bishops), occupied) & safe);
      bishops = Bitboards.withoutLowest(bishops);
      mgEg[0] += sign * (n - 7) * 5;
      mgEg[1] += sign * (n - 7) * 5;
    }
    long rooks = board.pieces(Piece.make(colour, Piece.ROOK));
    while (rooks != 0) {
      int n = Bitboards.count(Magics.rookAttacks(Bitboards.lowestSquare(rooks), occupied) & safe);
      rooks = Bitboards.withoutLowest(rooks);
      mgEg[0] += sign * (n - 7) * 2;
      mgEg[1] += sign * (n - 7) * 4;
    }
    long queens = board.pieces(Piece.make(colour, Piece.QUEEN));
    while (queens != 0) {
      int n = Bitboards.count(Magics.queenAttacks(Bitboards.lowestSquare(queens), occupied) & safe);
      queens = Bitboards.withoutLowest(queens);
      mgEg[0] += sign * (n - 14);
      mgEg[1] += sign * (n - 14) * 2;
    }
  }

  /** Middlegame only: in the endgame the king is a fighting piece, not a target. */
  private static void kingSafety(Board board, int colour, int sign, int[] mgEg) {
    int them = Colour.opposite(colour);
    int king = board.kingSquare(colour);
    int file = Square.file(king);
    int rank = Square.rank(king);
    int forward = colour == Colour.WHITE ? 1 : -1;
    long ourPawns = board.pieces(Piece.make(colour, Piece.PAWN));
    int score = 0;

    // Pawn shield: only meaningful for a castled-ish king on its back two ranks.
    int relativeRank = colour == Colour.WHITE ? rank : 7 - rank;
    if (relativeRank <= 1) {
      long shieldFiles = Bitboards.FILES[file] | ADJACENT_FILES[file];
      int r1 = rank + forward;
      int r2 = rank + 2 * forward;
      if (r1 >= 0 && r1 < 8) {
        score += 12 * Bitboards.count(ourPawns & shieldFiles & Bitboards.RANKS[r1]);
      }
      if (r2 >= 0 && r2 < 8) {
        score += 6 * Bitboards.count(ourPawns & shieldFiles & Bitboards.RANKS[r2]);
      }
      // Open files next to the king are highways for enemy rooks.
      long theirPawns = board.pieces(Piece.make(them, Piece.PAWN));
      for (int f = Math.max(0, file - 1); f <= Math.min(7, file + 1); f++) {
        if ((ourPawns & Bitboards.FILES[f]) == 0) {
          score -= (theirPawns & Bitboards.FILES[f]) == 0 ? 20 : 10;
        }
      }
    }

    // Attackers on the king zone (king square + its neighbours).
    long zone = Attacks.KING[king] | Bitboards.of(king);
    long occupied = board.occupied();
    int units = 0;
    int attackers = 0;
    for (int type = Piece.KNIGHT; type <= Piece.QUEEN; type++) {
      long pieces = board.pieces(Piece.make(them, type));
      while (pieces != 0) {
        int sq = Bitboards.lowestSquare(pieces);
        pieces = Bitboards.withoutLowest(pieces);
        long attacks =
            switch (type) {
              case Piece.KNIGHT -> Attacks.KNIGHT[sq];
              case Piece.BISHOP -> Magics.bishopAttacks(sq, occupied);
              case Piece.ROOK -> Magics.rookAttacks(sq, occupied);
              default -> Magics.queenAttacks(sq, occupied);
            };
        int hits = Bitboards.count(attacks & zone);
        if (hits > 0) {
          attackers++;
          units += KING_ATTACK_WEIGHT[type] * hits;
        }
      }
    }
    if (attackers >= 2 && board.pieces(Piece.make(them, Piece.QUEEN)) != 0) {
      score -= Math.min(400, units * units / 2);
    }
    mgEg[0] += sign * score;
  }

  /** Endgame helper: winning side pushes the losing king to the edge and approaches it. */
  private static int mopUp(Board board, int whiteScore, int phase) {
    if (phase > 10 || Math.abs(whiteScore) < 250) {
      return 0;
    }
    int winner = whiteScore > 0 ? Colour.WHITE : Colour.BLACK;
    int loser = Colour.opposite(winner);
    if (board.pieces(Piece.make(loser, Piece.PAWN)) != 0) {
      return 0; // only when the defender has no pawns left to create counterplay
    }
    int loserKing = board.kingSquare(loser);
    int winnerKing = board.kingSquare(winner);
    int centreDistance =
        Math.max(3 - Square.file(loserKing), Square.file(loserKing) - 4)
            + Math.max(3 - Square.rank(loserKing), Square.rank(loserKing) - 4);
    int kingDistance =
        Math.abs(Square.file(loserKing) - Square.file(winnerKing))
            + Math.abs(Square.rank(loserKing) - Square.rank(winnerKing));
    int bonus = centreDistance * 10 + (14 - kingDistance) * 4;
    return winner == Colour.WHITE ? bonus : -bonus;
  }
}
