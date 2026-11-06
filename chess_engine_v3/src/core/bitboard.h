#pragma once
// Bitboards and attack tables.
//
// Leapers (pawn, knight, king) use plain 64-entry tables. Sliders (bishop, rook) use PEXT tables:
// for a rook on d4 only the squares on its rank/file (minus the edges) can block it - that's its
// "mask", at most 12 squares. PEXT (a BMI2 CPU instruction) packs exactly those occupancy bits into
// a small integer, which indexes a pre-computed table of attack sets. This does the same job as
// v1's magic numbers (phase2.Magics) without having to search for magics: one instruction, no
// multiply, no collisions.

#include <bit>
#include <cstdint>

#include "core/types.h"

#if defined(V3_BMI2)
#include <immintrin.h>
#endif

namespace v3 {

constexpr Bitboard bb(Square sq) { return Bitboard(1) << sq; }
constexpr Bitboard bb(int sq) { return Bitboard(1) << sq; }

constexpr Bitboard FILE_A = 0x0101010101010101ULL;
constexpr Bitboard FILE_H = FILE_A << 7;
constexpr Bitboard RANK_1 = 0xFFULL;
constexpr Bitboard RANK_2 = RANK_1 << 8;
constexpr Bitboard RANK_4 = RANK_1 << 24;
constexpr Bitboard RANK_5 = RANK_1 << 32;
constexpr Bitboard RANK_7 = RANK_1 << 48;
constexpr Bitboard RANK_8 = RANK_1 << 56;

inline int popcount(Bitboard b) { return std::popcount(b); }
inline Square lsb(Bitboard b) { return Square(std::countr_zero(b)); }
/** Returns the lowest set square and clears it from {@code b}. */
inline Square pop_lsb(Bitboard& b) {
  Square sq = lsb(b);
  b &= b - 1;
  return sq;
}
inline bool more_than_one(Bitboard b) { return (b & (b - 1)) != 0; }

namespace attacks {

extern Bitboard pawn[2][64];
extern Bitboard knight[64];
extern Bitboard king[64];
/** Squares strictly between a and b if they share a line (rank/file/diagonal), else 0. */
extern Bitboard between[64][64];
/** The whole line (edge to edge) through a and b if they share one, else 0. */
extern Bitboard line[64][64];

struct SliderTable {
  Bitboard mask;
  Bitboard* attacks;
};
extern SliderTable bishop_table[64];
extern SliderTable rook_table[64];

/** Builds every table. Called once at start-up (cheap: about 1 ms). */
void init();

inline Bitboard bishop(Square sq, Bitboard occupied) {
  const SliderTable& t = bishop_table[sq];
  return t.attacks[_pext_u64(occupied, t.mask)];
}
inline Bitboard rook(Square sq, Bitboard occupied) {
  const SliderTable& t = rook_table[sq];
  return t.attacks[_pext_u64(occupied, t.mask)];
}
inline Bitboard queen(Square sq, Bitboard occupied) {
  return bishop(sq, occupied) | rook(sq, occupied);
}

/** Attacks of a non-pawn piece type from {@code sq}. */
inline Bitboard of(PieceType type, Square sq, Bitboard occupied) {
  switch (type) {
    case KNIGHT: return knight[sq];
    case BISHOP: return bishop(sq, occupied);
    case ROOK: return rook(sq, occupied);
    case QUEEN: return queen(sq, occupied);
    case KING: return king[sq];
    default: return 0;
  }
}

/** Slow ray walk used to build the tables (and by the tests as an independent check). */
Bitboard slider_slow(PieceType type, Square sq, Bitboard occupied);

}  // namespace attacks

/** True if this CPU has the BMI2 instructions the slider tables need. */
bool cpu_has_bmi2();

}  // namespace v3
