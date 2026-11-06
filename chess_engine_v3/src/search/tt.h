#pragma once
// Transposition table (step 3): remembers searched positions.
//
// Layout: buckets of 4 entries = 64 bytes = one CPU cache line, so a probe costs one memory fetch
// and finds the position in any of 4 slots (v2 had 1 slot per index: a collision always evicted).
// Each 16-byte entry stores the position's key XOR its data word ("lockless hashing"): when several
// search threads (step 4) write the same entry at once, a torn half-written entry no longer passes
// the key check, so it is never trusted.
//
//   data: move 0-15 | score 16-31 | static eval 32-47 | depth 48-55 | bound 56-57 | age 58-63
//
// Replacement inside a bucket: the same position is overwritten if the new result is exact, deeper
// (or nearly as deep), or from a newer search; otherwise the entry with the lowest
// "depth - 8 x age difference" is evicted (old and shallow results go first).

#include <cstdint>
#include <memory>

#include "core/types.h"

namespace v3 {

constexpr int INFINITE_SCORE = 32000;
constexpr int MATE = 30000;
constexpr int MATE_THRESHOLD = MATE - 1000;
constexpr int MAX_PLY = 128;
constexpr int NO_EVAL = -32001;  // "no static eval stored" (e.g. the node was in check)

enum Bound : int { BOUND_EXACT = 0, BOUND_LOWER = 1, BOUND_UPPER = 2 };

struct TTHit {
  Move move = MOVE_NONE;
  int score = 0;  // already adjusted to "relative to the root" for the probing ply
  int eval = NO_EVAL;
  int depth = 0;
  Bound bound = BOUND_UPPER;
};

class TranspositionTable {
 public:
  explicit TranspositionTable(int megabytes = 16) { resize(megabytes); }

  void resize(int megabytes);
  void clear();
  void new_search() { age_ = (age_ + 1) & 63; }
  bool probe(Key key, int ply, TTHit& hit) const;
  void store(Key key, Move move, int score, int eval, int depth, Bound bound, int ply);
  /** Permille of entries (sampled from the first 250 buckets) written by the current search. */
  int hashfull() const;
  /** Hint the CPU to start loading this key's bucket (used right after a move is made). */
  void prefetch(Key key) const;

 private:
  struct Entry {
    std::uint64_t key_xor;  // key ^ data
    std::uint64_t data;
  };
  struct alignas(64) Bucket {
    Entry e[4];
  };
  Bucket& bucket(Key key) const { return buckets_[key & mask_]; }

  std::unique_ptr<Bucket[]> buckets_;
  std::uint64_t count_ = 0;
  std::uint64_t mask_ = 0;
  int age_ = 0;
};

}  // namespace v3
