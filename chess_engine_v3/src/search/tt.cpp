#include "search/tt.h"

#include <immintrin.h>

#include <algorithm>
#include <bit>
#include <cstring>

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

Move d_move(std::uint64_t d) { return Move(d & 0xFFFF); }
int d_score(std::uint64_t d) { return std::int16_t((d >> 16) & 0xFFFF); }
int d_eval(std::uint64_t d) { return std::int16_t((d >> 32) & 0xFFFF); }
int d_depth(std::uint64_t d) { return int((d >> 48) & 0xFF); }
Bound d_bound(std::uint64_t d) { return Bound((d >> 56) & 0x3); }
int d_age(std::uint64_t d) { return int((d >> 58) & 63); }

std::uint64_t pack(Move move, int score, int eval, int depth, Bound bound, int age) {
  return std::uint64_t(move) | (std::uint64_t(std::uint16_t(score)) << 16) |
         (std::uint64_t(std::uint16_t(eval)) << 32) | (std::uint64_t(depth & 0xFF) << 48) |
         (std::uint64_t(bound & 3) << 56) | (std::uint64_t(age & 63) << 58);
}

}  // namespace

void TranspositionTable::resize(int megabytes) {
  std::uint64_t bytes = std::uint64_t(std::max(1, megabytes)) * 1024 * 1024;
  count_ = std::bit_floor(std::max<std::uint64_t>(1, bytes / sizeof(Bucket)));
  buckets_ = std::make_unique<Bucket[]>(count_);
  mask_ = count_ - 1;
  clear();
}

void TranspositionTable::clear() {
  std::memset(static_cast<void*>(buckets_.get()), 0, count_ * sizeof(Bucket));
  age_ = 0;
}

void TranspositionTable::prefetch(Key key) const {
  _mm_prefetch(reinterpret_cast<const char*>(&bucket(key)), _MM_HINT_T0);
}

bool TranspositionTable::probe(Key key, int ply, TTHit& hit) const {
  const Bucket& b = bucket(key);
  for (const Entry& e : b.e) {
    std::uint64_t data = e.data;
    if (data != 0 && (e.key_xor ^ data) == key) {
      hit.move = d_move(data);
      hit.score = score_from_tt(d_score(data), ply);
      hit.eval = d_eval(data);
      hit.depth = d_depth(data);
      hit.bound = d_bound(data);
      return true;
    }
  }
  return false;
}

void TranspositionTable::store(Key key, Move move, int score, int eval, int depth, Bound bound,
                               int ply) {
  Bucket& b = bucket(key);
  Entry* slot = nullptr;
  for (Entry& e : b.e) {
    if (e.data != 0 && (e.key_xor ^ e.data) == key) {
      slot = &e;
      break;
    }
  }
  depth = std::clamp(depth, 0, 255);
  if (slot) {
    std::uint64_t old = slot->data;
    // Keep a deeper result about the same position unless the new one is exact or newer.
    if (bound != BOUND_EXACT && depth + 4 <= d_depth(old) && d_age(old) == age_) return;
    if (move == MOVE_NONE) move = d_move(old);  // don't lose a known best move
  } else {
    // Evict the least valuable entry: empty first, then old and shallow.
    int worst = 1 << 30;
    for (Entry& e : b.e) {
      if (e.data == 0) {
        slot = &e;
        break;
      }
      int rel_age = (age_ - d_age(e.data)) & 63;
      int value = d_depth(e.data) - 8 * rel_age;
      if (value < worst) {
        worst = value;
        slot = &e;
      }
    }
  }
  std::uint64_t data = pack(move, score_to_tt(score, ply), eval, depth, bound, age_);
  slot->data = data;
  slot->key_xor = key ^ data;
}

int TranspositionTable::hashfull() const {
  std::uint64_t buckets = std::min<std::uint64_t>(250, count_);
  int used = 0;
  for (std::uint64_t i = 0; i < buckets; i++)
    for (const Entry& e : buckets_[i].e)
      if (e.data != 0 && d_age(e.data) == age_) used++;
  return int(used * 1000 / (buckets * 4));
}

}  // namespace v3
