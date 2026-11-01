package phase4;

import phase1.Move;
import phase2.MoveGenerator;
import phase2.MoveList;
import phase2.Position;
import phase3.SearchContext;
import phase3.SearchLimits;
import phase3.Searcher;

/**
 * Search v1 → {@code engine-v1.0}. Your Learnings 1 (#11-#18), done properly:
 *
 * <ul>
 *   <li><b>Negamax with alpha-beta.</b> Minimax where "my score = −(opponent's best score)". Alpha
 *       = the best score we are already guaranteed, beta = the most the opponent will allow. If a
 *       move scores ≥ beta, the opponent would never let us reach this position, so the remaining
 *       moves are skipped (a "cutoff").
 *   <li><b>Quiescence search.</b> At depth 0 we don't evaluate immediately: we keep searching
 *       captures until the position is quiet, so we never stop right after QxP and miss PxQ (the
 *       "horizon effect"). The side to move may also "stand pat" (decline all captures).
 *   <li><b>Iterative deepening.</b> Search depth 1, then 2, then 3, … until time runs out. Earlier
 *       iterations fill the transposition table, whose best moves make the next depth faster; and
 *       when time is up we always have the result of the last finished depth.
 *   <li><b>Transposition table</b> ({@link TranspositionTable}) and <b>move ordering</b> ({@link
 *       MoveOrdering}).
 *   <li><b>Draws</b>: any repetition or the 50-move rule inside the search counts as 0.
 *   <li><b>Mate scores</b>: being mated at ply p scores {@code -MATE + p}, so faster mates are
 *       preferred and slower defeats are resisted. "Mate distance pruning" skips lines that can't
 *       beat a mate already found.
 * </ul>
 *
 * Deliberately left for Phase 6 (each will be proven by SPRT): killers, history, PVS, null move,
 * LMR, check extensions, aspiration windows and a richer evaluation.
 */
public class Search implements Searcher {

  public static final int INFINITY = 32_000;
  public static final int MATE = 30_000;
  public static final int MATE_THRESHOLD = MATE - 1_000;
  public static final int MAX_PLY = 128;

  protected TranspositionTable tt = new TranspositionTable(16);

  protected final MoveList[] moveLists = new MoveList[MAX_PLY + 1];
  protected final int[][] moveScores = new int[MAX_PLY + 1][MoveList.CAPACITY];
  protected final int[][] pvTable = new int[MAX_PLY + 1][MAX_PLY + 1];
  protected final int[] pvLength = new int[MAX_PLY + 1];

  protected Position pos;
  protected SearchContext context;
  protected long nodes;
  protected long nodeLimit;
  protected int selDepth;
  protected boolean aborted;
  protected long startNanos;
  protected long hardDeadlineNanos;

  public Search() {
    for (int i = 0; i <= MAX_PLY; i++) {
      moveLists[i] = new MoveList();
    }
  }

  @Override
  public String name() {
    return "v1";
  }

  @Override
  public void newGame() {
    tt.clear();
  }

  @Override
  public void setHashSizeMb(int mb) {
    tt.resize(mb);
  }

  @Override
  public int bestMove(Position position, SearchLimits limits, SearchContext ctx) {
    this.pos = position;
    this.context = ctx;
    nodes = 0;
    aborted = false;
    selDepth = 0;
    nodeLimit = limits.nodes > 0 ? limits.nodes : Long.MAX_VALUE;
    tt.newSearch();

    MoveList rootMoves = new MoveList();
    MoveGenerator.generate(position, rootMoves);
    if (rootMoves.size() == 0) {
      return Move.NULL;
    }

    TimeManager time = new TimeManager(limits, position.sideToMove());
    startNanos = System.nanoTime();
    hardDeadlineNanos =
        time.hardLimitMs() > 0 ? startNanos + time.hardLimitMs() * 1_000_000L : Long.MAX_VALUE;
    int maxDepth = limits.depth > 0 ? Math.min(limits.depth, MAX_PLY - 1) : MAX_PLY - 1;

    int bestMove = rootMoves.get(0);
    for (int depth = 1; depth <= maxDepth; depth++) {
      int score = rootSearch(depth);
      if (aborted) {
        // A move that beat the previous best during the unfinished iteration is still safe to use.
        if (pvLength[0] > 0 && pvTable[0][0] != Move.NULL) {
          bestMove = pvTable[0][0];
        }
        break;
      }
      bestMove = pvTable[0][0];
      long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
      ctx.info(infoLine(depth, score, elapsedMs));

      boolean mateFound = Math.abs(score) >= MATE_THRESHOLD;
      if (mateFound && limits.depth < 0 && depth >= MATE - Math.abs(score)) {
        break; // a forced mate is fully proven; deeper search can't change it
      }
      if (time.stopAfterIteration(elapsedMs)) {
        break;
      }
    }
    return bestMove;
  }

  /** Overridden by later versions (e.g. aspiration windows in Phase 6). */
  protected int rootSearch(int depth) {
    return negamax(depth, 0, -INFINITY, INFINITY);
  }

  protected int negamax(int depth, int ply, int alpha, int beta) {
    pvLength[ply] = ply;
    if ((nodes & 2047) == 0) {
      checkLimits();
    }
    if (aborted) {
      return 0;
    }

    if (ply > 0) {
      if (isDraw()) {
        return 0;
      }
      // Mate distance pruning: even mating right now can't beat a shorter mate found elsewhere.
      alpha = Math.max(alpha, -MATE + ply);
      beta = Math.min(beta, MATE - ply - 1);
      if (alpha >= beta) {
        return alpha;
      }
    }
    if (depth <= 0) {
      return quiescence(ply, alpha, beta);
    }
    if (ply >= MAX_PLY - 1) {
      return Evaluation.evaluate(pos.board());
    }
    nodes++;

    long key = pos.key();
    long entry = tt.probe(key);
    int hashMove = Move.NULL;
    if (entry != TranspositionTable.MISS) {
      hashMove = TranspositionTable.move(entry);
      if (ply > 0 && TranspositionTable.depth(entry) >= depth) {
        int ttScore = TranspositionTable.score(entry, ply);
        int bound = TranspositionTable.bound(entry);
        if (bound == TranspositionTable.EXACT
            || (bound == TranspositionTable.LOWER && ttScore >= beta)
            || (bound == TranspositionTable.UPPER && ttScore <= alpha)) {
          return ttScore;
        }
      }
    }

    MoveList moves = moveLists[ply];
    MoveGenerator.generate(pos, moves);
    if (moves.size() == 0) {
      return pos.inCheck() ? -MATE + ply : 0; // checkmate or stalemate
    }
    int[] scores = moveScores[ply];
    MoveOrdering.score(pos.board(), moves, scores, hashMove);

    int originalAlpha = alpha;
    int bestScore = -INFINITY;
    int bestMove = Move.NULL;
    for (int i = 0; i < moves.size(); i++) {
      MoveOrdering.pickNext(moves, scores, i);
      int move = moves.get(i);
      pos.makeMove(move);
      int score = -negamax(depth - 1, ply + 1, -beta, -alpha);
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
            break; // cutoff
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
      return Evaluation.evaluate(pos.board());
    }

    boolean inCheck = pos.inCheck();
    MoveList moves = moveLists[ply];
    int bestScore;
    if (inCheck) {
      // In check there is no "standing pat": every evasion must be considered.
      MoveGenerator.generate(pos, moves);
      if (moves.size() == 0) {
        return -MATE + ply;
      }
      bestScore = -INFINITY;
    } else {
      int standPat = Evaluation.evaluate(pos.board());
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

  /** 50-move rule, or the current position already occurred since the last irreversible move. */
  protected boolean isDraw() {
    int halfmove = pos.board().halfmoveClock();
    if (halfmove >= 100) {
      return true;
    }
    long key = pos.key();
    int history = pos.historySize();
    int oldest = Math.max(0, history - halfmove);
    for (int i = history - 2; i >= oldest; i -= 2) {
      if (pos.keyBefore(i) == key) {
        return true;
      }
    }
    return false;
  }

  /** Triangular PV table: this ply's line = this move + the child's line. */
  protected void updatePv(int ply, int move) {
    pvTable[ply][ply] = move;
    int childLength = pvLength[ply + 1];
    for (int i = ply + 1; i < childLength; i++) {
      pvTable[ply][i] = pvTable[ply + 1][i];
    }
    pvLength[ply] = Math.max(childLength, ply + 1);
  }

  protected void checkLimits() {
    if (context.isStopped() || nodes >= nodeLimit || System.nanoTime() >= hardDeadlineNanos) {
      aborted = true;
    }
  }

  protected String infoLine(int depth, int score, long elapsedMs) {
    StringBuilder sb = new StringBuilder("info depth ").append(depth);
    sb.append(" seldepth ").append(Math.max(selDepth, depth));
    if (Math.abs(score) >= MATE_THRESHOLD) {
      int pliesToMate = MATE - Math.abs(score);
      int movesToMate = (pliesToMate + 1) / 2;
      sb.append(" score mate ").append(score > 0 ? movesToMate : -movesToMate);
    } else {
      sb.append(" score cp ").append(score);
    }
    sb.append(" nodes ").append(nodes);
    sb.append(" nps ").append(elapsedMs > 0 ? nodes * 1000 / elapsedMs : nodes);
    sb.append(" hashfull ").append(tt.hashfull());
    sb.append(" time ").append(elapsedMs);
    sb.append(" pv");
    for (int i = 0; i < pvLength[0]; i++) {
      sb.append(' ').append(Move.toUci(pvTable[0][i]));
    }
    return sb.toString();
  }
}
