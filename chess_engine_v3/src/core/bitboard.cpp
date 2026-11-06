#include "core/bitboard.h"

#include <cstdio>
#include <cstdlib>

#if defined(_MSC_VER)
#include <intrin.h>
#else
#include <cpuid.h>
#endif

namespace v3 {

std::string square_name(Square sq) {
  if (sq == SQ_NONE) return "-";
  return {char('a' + file_of(sq)), char('1' + rank_of(sq))};
}

std::string move_to_uci(Move m) {
  if (m == MOVE_NONE) return "0000";
  std::string s = square_name(move_from(m)) + square_name(move_to(m));
  if (is_promotion(m)) s += "?nbrq?"[promotion_type(m)];
  return s;
}

bool cpu_has_bmi2() {
#if defined(_MSC_VER)
  int info[4];
  __cpuidex(info, 7, 0);
  return (info[1] & (1 << 8)) != 0;
#else
  unsigned a, b, c, d;
  if (!__get_cpuid_count(7, 0, &a, &b, &c, &d)) return false;
  return (b & (1u << 8)) != 0;
#endif
}

namespace attacks {

Bitboard pawn[2][64];
Bitboard knight[64];
Bitboard king[64];
Bitboard between[64][64];
Bitboard line[64][64];
SliderTable bishop_table[64];
SliderTable rook_table[64];

namespace {

// 102,400 rook entries + 5,248 bishop entries: one shared pool, each square gets a slice.
Bitboard rook_pool[102400];
Bitboard bishop_pool[5248];

constexpr int ROOK_DIRS[4][2] = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
constexpr int BISHOP_DIRS[4][2] = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

Bitboard step(int sq, int df, int dr) {
  int f = file_of(sq) + df, r = rank_of(sq) + dr;
  return (f >= 0 && f < 8 && r >= 0 && r < 8) ? bb(make_square(f, r)) : 0;
}

/** Slider mask: the squares whose occupancy matters (rays without their last square). */
Bitboard slider_mask(PieceType type, Square sq) {
  const auto& dirs = type == ROOK ? ROOK_DIRS : BISHOP_DIRS;
  Bitboard mask = 0;
  for (const auto& d : dirs) {
    int f = file_of(sq) + d[0], r = rank_of(sq) + d[1];
    // Keep a square only if the ray continues past it (the last square never blocks anything).
    while (f + d[0] >= 0 && f + d[0] < 8 && r + d[1] >= 0 && r + d[1] < 8) {
      mask |= bb(make_square(f, r));
      f += d[0];
      r += d[1];
    }
  }
  return mask;
}

void init_slider(PieceType type, SliderTable* table, Bitboard* pool) {
  Bitboard* next = pool;
  for (int s = 0; s < 64; s++) {
    Square sq = Square(s);
    Bitboard mask = slider_mask(type, sq);
    table[s] = {mask, next};
    // Enumerate every subset of the mask (the "carry-rippler" trick) and store its attack set
    // at the index PEXT will produce for it.
    Bitboard subset = 0;
    do {
      next[_pext_u64(subset, mask)] = slider_slow(type, sq, subset);
      subset = (subset - mask) & mask;
    } while (subset);
    next += Bitboard(1) << popcount(mask);
  }
}

}  // namespace

Bitboard slider_slow(PieceType type, Square sq, Bitboard occupied) {
  const auto& dirs = type == ROOK ? ROOK_DIRS : BISHOP_DIRS;
  Bitboard result = 0;
  for (const auto& d : dirs) {
    int f = file_of(sq) + d[0], r = rank_of(sq) + d[1];
    while (f >= 0 && f < 8 && r >= 0 && r < 8) {
      Bitboard b = bb(make_square(f, r));
      result |= b;
      if (occupied & b) break;
      f += d[0];
      r += d[1];
    }
  }
  return result;
}

void init() {
  static bool done = false;
  if (done) return;
  done = true;
  if (!cpu_has_bmi2()) {
    std::fprintf(stderr, "chess_engine_v3 needs a CPU with BMI2 (PEXT); this one has none.\n");
    std::exit(1);
  }
  for (int sq = 0; sq < 64; sq++) {
    pawn[WHITE][sq] = step(sq, -1, 1) | step(sq, 1, 1);
    pawn[BLACK][sq] = step(sq, -1, -1) | step(sq, 1, -1);
    knight[sq] = step(sq, 1, 2) | step(sq, 2, 1) | step(sq, 2, -1) | step(sq, 1, -2) |
                 step(sq, -1, -2) | step(sq, -2, -1) | step(sq, -2, 1) | step(sq, -1, 2);
    king[sq] = step(sq, 1, 0) | step(sq, -1, 0) | step(sq, 0, 1) | step(sq, 0, -1) |
               step(sq, 1, 1) | step(sq, 1, -1) | step(sq, -1, 1) | step(sq, -1, -1);
  }
  init_slider(ROOK, rook_table, rook_pool);
  init_slider(BISHOP, bishop_table, bishop_pool);

  for (int a = 0; a < 64; a++) {
    for (int b = 0; b < 64; b++) {
      between[a][b] = line[a][b] = 0;
      if (a == b) continue;
      for (PieceType t : {BISHOP, ROOK}) {
        if (slider_slow(t, Square(a), 0) & bb(b)) {
          line[a][b] = (slider_slow(t, Square(a), 0) & slider_slow(t, Square(b), 0)) | bb(a) | bb(b);
          between[a][b] = slider_slow(t, Square(a), bb(b)) & slider_slow(t, Square(b), bb(a));
        }
      }
    }
  }
}

}  // namespace attacks
}  // namespace v3
