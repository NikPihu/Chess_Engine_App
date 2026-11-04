package search;

import nnue.Accumulator;
import nnue.Network;
import phase1.Board;
import phase1.Move;
import phase1.Piece;
import phase2.MoveGenerator;
import phase2.MoveList;
import phase2.Position;
import phase3.SearchContext;
import phase3.SearchLimits;
import phase4.Evaluation;
import phase4.MoveOrdering;
import phase4.Search;
import phase4.TranspositionTable;
import phase6.Features;
import phase6.History;
import phase6.Killers;
import phase6.MoveOrdering2;
import phase6.Search2;

/**
 * chess_engine_v2's search: exactly chess_engine_v1's proven search ({@link Search2} with the
 * features that passed SPRT), but the static evaluation is the NNUE {@link Network} instead of
 * hand-written PeSTO tables.
 *
 * <p>The one structural change: every node owns an {@link Accumulator} ({@code acc[ply]}). Before a
 * move is made, the child's accumulator is copied from the parent and updated for that move's few
 * changed features, so evaluating a position costs ~1,000 multiply-adds instead of re-reading the
 * whole board. Unmaking a move needs nothing: the parent's accumulator is untouched one level up.
 *
 * <p>Without a network ({@code null}) it falls back to the PeSTO evaluation, so the engine still
 * plays (and says so) if no {@code .nnue} file is available.
 */
public class NnueSearch extends Search {

  private static final int[][] LMR = new int[64][64];

  static {
    for (int depth = 1; depth < 64; depth++) {
      for (int moveIndex = 1; moveIndex < 64; moveIndex++) {
        LMR[depth][moveIndex] = (int) (0.75 + Math.log(depth) * Math.log(moveIndex) / 2.25);
      }
    }
  }

  private final int features = Search2.PROVEN;
  private final Killers killers = new Killers();
  private final History history = new History();
  private final Accumulator[] acc = new Accumulator[MAX_PLY + 2];
  private Network network;
  private String networkName;
  private int previousScore = 0;
  private boolean announced = false;

  public NnueSearch(Network network, String networkName) {
    for (int i = 0; i < acc.length; i++) {
      acc[i] = new Accumulator();
    }
    setNetwork(network, networkName);
  }

  @Override
  public String name() {
    return "nnue";
  }

  public void setNetwork(Network network, String networkName) {
    this.network = network;
    this.networkName = network == null ? "none (PeSTO fallback)" : networkName;
  }

  public String networkName() {
    return networkName;
  }

  @Override
  public void newGame() {
    super.newGame();
    killers.clear();
    history.clear();
  }

  @Override
  public int bestMove(Position position, SearchLimits limits, SearchContext ctx) {
    killers.clear();
    history.age();
    previousScore = 0;
    if (network != null) {
      acc[0].refresh(network, position.board());
      if (!announced) {
        ctx.info("info string nnue network " + networkName + ", " + nnue.Simd.describe());
        announced = true;
      }
    } else {
      ctx.info("info string no NNUE network loaded, using PeSTO evaluation");
    }
    return super.bestMove(position, limits, ctx);
  }

  private boolean on(int feature) {
    return Features.on(features, feature);
  }

  private int evaluate(int ply) {
    if (network == null) {
      return Evaluation.evaluate(pos.board());
    }
    return acc[ply].evaluate(network, pos.sideToMove());
  }

  /** Plays {@code move} and prepares the child's accumulator. */
  private void make(int move, int ply) {
    if (network != null) {
      acc[ply + 1].update(network, pos.board(), move, acc[ply]);
    }
    pos.makeMove(move);
  }

  private void makeNull(int ply) {
    if (network != null) {
      acc[ply + 1].copyFrom(acc[ply]);
    }
    pos.makeNullMove();
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
      return evaluate(ply);
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

    int staticEval = inCheck ? -INFINITY : evaluate(ply);

    if (on(Features.REVERSE_FUTILITY)
        && !pvNode
        && !inCheck
        && depth <= 6
        && Math.abs(beta) < MATE_THRESHOLD
        && staticEval - 80 * depth >= beta) {
      return staticEval;
    }

    if (on(Features.NULL_MOVE)
        && allowNull
        && !pvNode
        && !inCheck
        && depth >= 3
        && staticEval >= beta
        && hasPiecesOtherThanPawns(pos.board())) {
      int reduction = 3 + depth / 6;
      makeNull(ply);
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
      make(move, ply);
      boolean givesCheck = pos.inCheck();
      int newDepth = depth - 1;
      int score;

      if (i == 0) {
        score = -search(newDepth, ply + 1, -beta, -alpha, true);
      } else {
        int reduction = 0;
        if (on(Features.LMR) && depth >= 3 && i >= 3 && quiet && !inCheck && !givesCheck) {
          reduction =
              Math.max(0, Math.min(LMR[Math.min(depth, 63)][Math.min(i, 63)], newDepth - 1));
        }
        boolean fullSearch = true;
        score = alpha;
        if (reduction > 0) {
          score = -search(newDepth - reduction, ply + 1, -alpha - 1, -alpha, true);
          fullSearch = score > alpha;
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
      return evaluate(ply);
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
      int standPat = evaluate(ply);
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
      make(move, ply);
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

  private static boolean hasPiecesOtherThanPawns(Board board) {
    int side = board.sideToMove();
    long pawnsAndKing =
        board.pieces(Piece.make(side, Piece.PAWN)) | board.pieces(Piece.make(side, Piece.KING));
    return (board.piecesOf(side) & ~pawnsAndKing) != 0;
  }
}
