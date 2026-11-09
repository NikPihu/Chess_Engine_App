package com.gambyte.analysis;

import com.gambyte.common.ApiException;
import com.gambyte.engine.EngineLimits;
import com.gambyte.engine.EnginePool;
import com.gambyte.engine.UciEngine;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import phase2.Position;
import phase2.UciMoveParser;
import phase3.San;

/** One-position engine analysis for the analysis board ("what does the engine think here?"). */
@Service
public class AnalysisService {

  private final EnginePool pool;

  public AnalysisService(EnginePool pool) {
    this.pool = pool;
  }

  /**
   * Engine verdict on a position.
   *
   * @param whiteCp score from White's side in centipawns (mate counts as ±10000)
   * @param pvSan the expected continuation in SAN, for display
   */
  public record Eval(String bestMove, String bestSan, int whiteCp, Integer mate, int depth, List<String> pvSan) {}

  public Eval evaluate(String fen, long nodes) {
    Position pos;
    try {
      pos = Position.fromFen(fen);
    } catch (RuntimeException e) {
      throw ApiException.badRequest("bad_fen", "That FEN is not a valid position");
    }
    UciEngine.Result r = pool.search(fen, List.of(), EngineLimits.nodes(nodes));
    boolean whiteToMove = pos.sideToMove() == phase1.Colour.WHITE;
    int cp = r.scoreOrMate();
    Integer mate = r.mateIn() == null ? null : (whiteToMove ? r.mateIn() : -r.mateIn());
    List<String> san = toSan(fen, r.pv());
    return new Eval(r.bestMove(), san.isEmpty() ? null : san.getFirst(), whiteToMove ? cp : -cp, mate, r.depth(), san);
  }

  /** Converts UCI moves to SAN, stopping at the first illegal one (a truncated PV). */
  public static List<String> toSan(String fen, List<String> uciMoves) {
    Position p = Position.fromFen(fen);
    List<String> out = new ArrayList<>();
    for (String m : uciMoves) {
      int move = UciMoveParser.parse(p, m);
      if (move == phase1.Move.NULL) break;
      out.add(San.toSan(p, move));
      p.makeMove(move);
    }
    return out;
  }
}
