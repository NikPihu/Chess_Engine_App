#pragma once
// Syzygy endgame tablebases (via the Fathom library, third_party/fathom, MIT).
//
// Tablebases are solved endgames: for every position with few pieces (here up to 5) they store
// whether it is a win, draw or loss (WDL) and how many moves until the next capture or pawn move
// (DTZ). They come from retrograde analysis (working backwards from every checkmate), not from any
// engine's judgement.
//
//   in the search: a position with <= 5 pieces, no castling rights and a fresh 50-move counter is
//                  looked up instead of searched (WDL); a win scores just below a mate score.
//   at the root:   the DTZ table picks a move that keeps the result and converts within the
//                  50-move rule - this is what stops "won but fumbled" endgames on Lichess.

#include <atomic>
#include <cstdint>
#include <string>

#include "board/position.h"

namespace v3::syzygy {

/** A tablebase win: below every mate score, above every normal evaluation. */
constexpr int TB_WIN_SCORE = 28000;

/** Loads tables from {@code path} (folders separated by ';'). Returns the largest piece count
 *  available (0 = none found / disabled). Not thread-safe: call when no search is running. */
int init(const std::string& path);
int largest();

/** WDL for the side to move: -2 loss, -1 loss saved by the 50-move rule, 0 draw, 1 win spoiled by
 *  the 50-move rule, 2 win. Returns false if the position can't be probed. Thread-safe. */
bool probe_wdl(const Position& pos, int& wdl);

/** At the root: the move that preserves the tablebase result (DTZ-optimal), or MOVE_NONE. */
Move probe_root(const Position& pos, int& wdl);

/** Score of a WDL value at {@code ply} from the root. */
inline int wdl_to_score(int wdl, int ply) {
  return wdl == 2 ? TB_WIN_SCORE - ply : wdl == -2 ? -TB_WIN_SCORE + ply : wdl;
}

}  // namespace v3::syzygy
