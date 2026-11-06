#pragma once
// Legal move generation.
//
// v1 generated pseudo-legal moves and then played each one to see whether the king was left in
// check. v3 generates only legal moves directly, using two masks computed once per position:
//   - check mask: when in check by one piece, a non-king move must capture the checker or step
//     between it and the king; in double check only the king may move.
//   - pins: a piece standing alone between our king and an enemy slider may only move along that
//     line (line[king][piece]).
// King moves are tested against attacks with the king removed from the board (so it cannot "hide"
// behind itself along a slider's ray), and en passant - the one move that removes two pieces from
// one rank - is verified by simulating it on the bitboards.

#include <string>

#include "board/position.h"

namespace v3 {

struct MoveList {
  Move moves[256];
  int size = 0;

  void add(Move m) { moves[size++] = m; }
  Move* begin() { return moves; }
  Move* end() { return moves + size; }
  const Move* begin() const { return moves; }
  const Move* end() const { return moves + size; }
  bool contains(Move m) const;
};

enum GenType {
  ALL,    // every legal move
  NOISY,  // captures (incl. en passant) and promotions: what quiescence search looks at
  QUIET,  // the rest (ALL = NOISY + QUIET)
};

void generate(const Position& pos, MoveList& list, GenType type = ALL);

/** Finds the legal move with this UCI text ("e2e4", "e7e8q"), or MOVE_NONE. */
Move parse_uci_move(const Position& pos, const std::string& text);

}  // namespace v3
