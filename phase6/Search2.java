package phase6;

import phase1.Board;
import phase1.Move;
import phase1.Piece;
import phase2.MoveGenerator;
import phase2.MoveList;
import phase3.SearchContext;
import phase3.SearchLimits;
import phase4.MoveOrdering;
import phase4.Search;
import phase4.TranspositionTable;

/**
 * Search v2 → {@code engine-v2.0}. Builds on {@link Search} (v1, Phase 4) and adds, each behind a
 * {@link Features} switch:
 *
 * <ul>
 *   <li><b>Killers + history</b> move ordering ({@link MoveOrdering2}).
 *   <li><b>PVS</b> (principal variation search): after the first move, prove the others are worse
 *       with a cheap zero-width window [alpha, alpha+1]; only re-search with the full window if one
 *       surprises us. With good ordering most proofs succeed.
 *   <li><b>Null-move pruning</b>: "even if I pass, the opponent can't hurt me" → this position is
 *       so good we can stop. Searched with reduced depth; skipped in check and in pawn endings
 *       (zugzwang, where passing would be an advantage).
 *   <li><b>Late move reductions</b>: quiet moves late in the ordering are probably bad; search them
 *       shallower first, and only at full depth if they beat alpha.
 *   <li><b>Check extension</b>: positions in check are searched one ply deeper (forcing lines).
 *   <li><b>Aspiration windows</b>: from depth 5, search the root with a narrow window around the
 *       previous score; widen and retry if the score falls outside.
 *   <li><b>Reverse futility pruning</b>: near the leaves, if the static evaluation is far above
 *       beta, assume the opponent can't recover and cut.
 *   <li>Evaluation v2 ({@link Evaluation2}).
 * </ul>
 */
public class Search2 extends Search {

  private static final int[][] LMR = new int[64][64];

  static {
    for (int depth = 1; depth < 64; depth++) {
      for (int moveIndex = 1; moveIndex < 64; moveIndex++) {
        LMR[depth][moveIndex] = (int) (0.75 + Math.log(depth) * Math.log(moveIndex) / 2.25);
      }
    }
  }

  private int features = Features.ALL;
  private final Killers killers = new Killers();
  private final History history = new History();
  private int previousScore = 0;

  @Override
  public String name() {
    return "v2";
  }

  public void setFeatures(int features) {
    this.features = features;
  }

  public int features() {
    return features;
  }

  @Override
  public void newGame() {
    super.newGame();
    killers.clear();
    history.clear();
  }

  @Override
  public int bestMove(phase2.Position position, SearchLimits limits, SearchContext ctx) {
    killers.clear();
    history.age();
    previousScore = 0;
    return super.bestMove(position, limits, ctx);
  }

  private boolean on(int feature) {
    return Features.on(features, feature);
  }

  private int evaluate() {
    return Evaluation2.evaluate(pos.board(), features);
  }

  @Override
  protected int rootSearch(int depth) {
    if (!on(Features.ASPIRATION) || depth < 5) {
      previousScore = search(depth, 0, -INFINITY, INFINITY, false);
      return previousScore;
    }
    int delta = 25;
    int alpha = Math.max(-INFINITY, previousScore - delta);
    int beta = Math.min(INFINITY, previousScore + delta);
    while (true) {
      int score = search(depth, 0, alpha, beta, false);
      if (aborted) {
        return score;
      }
      if (score <= alpha) {
        alpha = Math.max(-INFINITY, alpha - delta);
      } else if (score >= beta) {
        beta = Math.min(INFINITY, beta + delta);
      } else {
        previousScore = score;
        return score;
      }
      delta *= 2;
      if (delta > 1000) {
        alpha = -INFINITY;
        beta = INFINITY;
      }
    }
  }

  @Override
  protected int negamax(int depth, int ply, int alpha, int beta) {
    return search(depth, ply, alpha, beta, true);
  }

  private int search(int depth, int ply, int alpha, int beta, boolean allowNull) {
    pvLength[ply] = ply;
    if ((nodes & 2047) == 0) {
      checkLimits();
    }
    if (aborted) {
      return 0;
    }
    boolean pvNode = beta - alpha > 1;

    if (ply > 0) {
      if (isDraw()) {
        return 0;
      }
      alpha = Math.max(alpha, -MATE + ply);
      beta = Math.min(beta, MATE - ply - 1);
      if (alpha >= beta) {
        return alpha;
      }
    }

    boolean inCheck = pos.inCheck();
    if (inCheck && on(Features.CHECK_EXTENSION)) {
      depth++;
    }
    if (depth <= 0) {
      return quiescence(ply, alpha, beta);
    }
    if (ply >= MAX_PLY - 1) {
      return evaluate();
    }
    nodes++;

    long key = pos.key();
    long entry = tt.probe(key);
    int hashMove = Move.NULL;
    if (entry != TranspositionTable.MISS) {
      hashMove = TranspositionTable.move(entry);
      if (ply > 0 && !pvNode && TranspositionTable.depth(entry) >= depth) {
        int ttScore = TranspositionTable.score(entry, ply);
        int bound = TranspositionTable.bound(entry);
        if (bound == TranspositionTable.EXACT
            || (bound == TranspositionTable.LOWER && ttScore >= beta)
            || (bound == TranspositionTable.UPPER && ttScore <= alpha)) {
          return ttScore;
        }
      }
    }

    int staticEval = inCheck ? -INFINITY : evaluate();

    // Reverse futility pruning.
    if (on(Features.REVERSE_FUTILITY)
        && !pvNode
        && !inCheck
        && depth <= 6
        && Math.abs(beta) < MATE_THRESHOLD
        && staticEval - 80 * depth >= beta) {
      return staticEval;
    }

    // Null-move pruning.
    if (on(Features.NULL_MOVE)
        && allowNull
        && !pvNode
        && !inCheck
        && depth >= 3
        && staticEval >= beta
        && hasPiecesOtherThanPawns(pos.board())) {
      int reduction = 3 + depth / 6;
      pos.makeNullMove();
      int score = -search(depth - 1 - reduction, ply + 1, -beta, -beta + 1, false);
      pos.unmakeNullMove();
      if (aborted) {
        return 0;
      }
      if (score >= beta) {
        return score >= MATE_THRESHOLD ? beta : score;
      }
    }

    MoveList moves = moveLists[ply];
    MoveGenerator.generate(pos, moves);
    if (moves.size() == 0) {
      return inCheck ? -MATE + ply : 0;
    }
    int[] scores = moveScores[ply];
    MoveOrdering2.score(
        pos.board(),
        moves,
        scores,
        hashMove,
        killers.first(ply),
        killers.second(ply),
        history,
        features);

    int side = pos.sideToMove();
    int originalAlpha = alpha;
    int bestScore = -INFINITY;
    int bestMove = Move.NULL;
    for (int i = 0; i < moves.size(); i++) {
      MoveOrdering.pickNext(moves, scores, i);
      int move = moves.get(i);
      boolean quiet = MoveOrdering2.isQuiet(pos.board(), move);
      pos.makeMove(move);
      boolean givesCheck = pos.inCheck();
      int newDepth = depth - 1;
      int score;

      if (i == 0) {
        score = -search(newDepth, ply + 1, -beta, -alpha, true);
      } else {
        int reduction = 0;
        if (on(Features.LMR) && depth >= 3 && i >= 3 && quiet && !inCheck && !givesCheck) {
          reduction = Math.min(LMR[Math.min(depth, 63)][Math.min(i, 63)], newDepth - 1);
          reduction = Math.max(0, reduction);
        }
        boolean fullSearch = true;
        if (reduction > 0) {
          score = -search(newDepth - reduction, ply + 1, -alpha - 1, -alpha, true);
          fullSearch = score > alpha;
        } else {
          score = alpha; // placeholder, replaced below
        }
        if (fullSearch) {
          if (on(Features.PVS)) {
            score = -search(newDepth, ply + 1, -alpha - 1, -alpha, true);
            if (score > alpha && score < beta) {
              score = -search(newDepth, ply + 1, -beta, -alpha, true);
            }
          } else {
            score = -search(newDepth, ply + 1, -beta, -alpha, true);
          }
        }
      }
      pos.unmakeMove(move);
      if (aborted) {
        return 0;
      }

      if (score > bestScore) {
        bestScore = score;
        bestMove = move;
        if (score > alpha) {
          alpha = score;
          updatePv(ply, move);
          if (alpha >= beta) {
            if (quiet) {
              if (on(Features.KILLERS)) {
                killers.add(ply, move);
              }
              if (on(Features.HISTORY)) {
                history.reward(side, Move.start(move), Move.target(move), depth);
              }
            }
            break;
          }
        }
      }
    }

    int bound =
        bestScore >= beta
            ? TranspositionTable.LOWER
            : bestScore > originalAlpha ? TranspositionTable.EXACT : TranspositionTable.UPPER;
    tt.store(key, bestMove, bestScore, depth, bound, ply);
    return bestScore;
  }

  /** Same as v1's quiescence search, but with evaluation v2. */
  @Override
  protected int quiescence(int ply, int alpha, int beta) {
    pvLength[ply] = ply;
    if ((nodes & 2047) == 0) {
      checkLimits();
    }
    if (aborted) {
      return 0;
    }
    nodes++;
    selDepth = Math.max(selDepth, ply);
    if (ply >= MAX_PLY - 1) {
      return evaluate();
    }
    boolean inCheck = pos.inCheck();
    MoveList moves = moveLists[ply];
    int bestScore;
    if (inCheck) {
      MoveGenerator.generate(pos, moves);
      if (moves.size() == 0) {
        return -MATE + ply;
      }
      bestScore = -INFINITY;
    } else {
      int standPat = evaluate();
      if (standPat >= beta) {
        return standPat;
      }
      alpha = Math.max(alpha, standPat);
      bestScore = standPat;
      MoveGenerator.generateCaptures(pos, moves);
    }
    int[] scores = moveScores[ply];
    MoveOrdering.score(pos.board(), moves, scores, Move.NULL);
    for (int i = 0; i < moves.size(); i++) {
      MoveOrdering.pickNext(moves, scores, i);
      int move = moves.get(i);
      pos.makeMove(move);
      int score = -quiescence(ply + 1, -beta, -alpha);
      pos.unmakeMove(move);
      if (aborted) {
        return 0;
      }
      if (score > bestScore) {
        bestScore = score;
        if (score > alpha) {
          alpha = score;
          updatePv(ply, move);
          if (alpha >= beta) {
            break;
          }
        }
      }
    }
    return bestScore;
  }

  /** Null move is unsafe in pawn endings (zugzwang), so require a knight/bishop/rook/queen. */
  private static boolean hasPiecesOtherThanPawns(Board board) {
    int side = board.sideToMove();
    long pawnsAndKing =
        board.pieces(Piece.make(side, Piece.PAWN)) | board.pieces(Piece.make(side, Piece.KING));
    return (board.piecesOf(side) & ~pawnsAndKing) != 0;
  }
}
