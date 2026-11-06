#include "search/tt.h"

#include <algorithm>
#include <bit>

namespace v3 {

namespace {
int score_to_tt(int score, int ply) {
  if (score >= MATE_THRESHOLD) return score + ply;
  if (score <= -MATE_THRESHOLD) return score - ply;
  return score;
}
int score_from_tt(int score, int ply) {
  if (score >= MATE_THRESHOLD) return score - ply;
  if (score <= -MATE_THRESHOLD) return score + ply;
  return score;
}
int entry_age(std::uint64_t d) { return int((d >> 42) & 0xFF); }
int entry_depth(std::uint64_t d) { return int((d >> 32) & 0xFF); }
Move entry_move(std::uint64_t d) { return Move(d & 0xFFFF); }
}  // namespace

void TranspositionTable::resize(int megabytes) {
  std::uint64_t bytes = std::uint64_t(std::max(1, megabytes)) * 1024 * 1024;
  std::uint64_t count = std::bit_floor(bytes / sizeof(Entry));
  table_.assign(count, Entry{0, 0});
  table_.shrink_to_fit();
  mask_ = count - 1;
  age_ = 0;
}

void TranspositionTable::clear() {
  std::fill(table_.begin(), table_.end(), Entry{0, 0});
  age_ = 0;
}

bool TranspositionTable::probe(Key key, int ply, TTHit& hit) const {
  const Entry& e = table_[key & mask_];
  if (e.key != key || e.data == 0) return false;
  hit.move = entry_move(e.data);
  hit.score = score_from_tt(std::int16_t((e.data >> 16) & 0xFFFF), ply);
  hit.depth = entry_depth(e.data);
  hit.bound = Bound((e.data >> 40) & 0x3);
  return true;
}

void TranspositionTable::store(Key key, Move move, int score, int depth, Bound bound, int ply) {
  Entry& e = table_[key & mask_];
  if (e.data != 0 && e.key != key && entry_age(e.data) == age_ && entry_depth(e.data) > depth)
    return;  // keep the deeper result from this search
  if (e.key == key && move == MOVE_NONE) move = entry_move(e.data);  // don't lose a known best move
  e.data = std::uint64_t(move) | (std::uint64_t(std::uint16_t(score_to_tt(score, ply))) << 16) |
           (std::uint64_t(std::max(0, depth) & 0xFF) << 32) | (std::uint64_t(bound & 3) << 40) |
           (std::uint64_t(age_ & 0xFF) << 42) | (std::uint64_t(1) << 62);
  e.key = key;
}

int TranspositionTable::hashfull() const {
  int samples = int(std::min<std::size_t>(1000, table_.size()));
  int used = 0;
  for (int i = 0; i < samples; i++)
    if (table_[i].data != 0 && entry_age(table_[i].data) == age_) used++;
  return used * 1000 / samples;
}

}  // namespace v3
