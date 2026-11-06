#pragma once
// Transposition table: remembers searched positions (best move, score, depth, bound).
// Step 2 is a faithful port of v1's phase4.TranspositionTable: one entry per slot, the slot is
// key & (size - 1), and an older entry survives only if it belongs to the current search, is about
// a different position and is deeper. Mate scores are stored relative to the node (P3 upgrades
// this to buckets + stored static eval).

#include <cstdint>
#include <vector>

#include "core/types.h"

namespace v3 {

constexpr int INFINITE_SCORE = 32000;
constexpr int MATE = 30000;
constexpr int MATE_THRESHOLD = MATE - 1000;
constexpr int MAX_PLY = 128;

enum Bound : int { BOUND_EXACT = 0, BOUND_LOWER = 1, BOUND_UPPER = 2 };

struct TTHit {
  Move move;
  int score;  // already adjusted to "relative to the root" for the probing ply
  int depth;
  Bound bound;
};

class TranspositionTable {
 public:
  explicit TranspositionTable(int megabytes = 16) { resize(megabytes); }

  void resize(int megabytes);
  void clear();
  void new_search() { age_ = (age_ + 1) & 0xFF; }
  bool probe(Key key, int ply, TTHit& hit) const;
  void store(Key key, Move move, int score, int depth, Bound bound, int ply);
  /** Permille of the first 1000 slots used by the current search (UCI "hashfull"). */
  int hashfull() const;

 private:
  struct Entry {
    Key key;
    std::uint64_t data;  // move 0-15 | score 16-31 | depth 32-39 | bound 40-41 | age 42-49 | used 62
  };
  std::vector<Entry> table_;
  std::uint64_t mask_ = 0;
  int age_ = 0;
};

}  // namespace v3
