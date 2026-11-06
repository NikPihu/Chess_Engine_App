#pragma once
// Basic chess types for chess_engine_v3. Same numbering as chess_engine_v1/v2 wherever it matters,
// so v2's network files and test data can be read unchanged:
//   squares  a1 = 0, b1 = 1, ..., h8 = 63   (square = rank * 8 + file)
//   moves    16 bits: start | target << 6 | flag << 12   (identical to v1's phase1.Move)

#include <cstdint>
#include <string>

namespace v3 {

using Bitboard = std::uint64_t;
using Key = std::uint64_t;

enum Color : int { WHITE = 0, BLACK = 1 };
constexpr Color operator~(Color c) { return Color(c ^ 1); }

enum PieceType : int { PAWN = 0, KNIGHT, BISHOP, ROOK, QUEEN, KING, PIECE_TYPE_NB };

// A piece is type + 6 * colour: 0..5 white pawn..king, 6..11 black pawn..king, 12 = empty.
enum Piece : int { NO_PIECE = 12, PIECE_NB = 12 };
constexpr Piece make_piece(Color c, PieceType t) { return Piece(t + 6 * c); }
constexpr PieceType type_of(Piece p) { return PieceType(p % 6); }
constexpr Color color_of(Piece p) { return Color(p / 6); }

enum Square : int {
  A1, B1, C1, D1, E1, F1, G1, H1,
  A2, B2, C2, D2, E2, F2, G2, H2,
  A3, B3, C3, D3, E3, F3, G3, H3,
  A4, B4, C4, D4, E4, F4, G4, H4,
  A5, B5, C5, D5, E5, F5, G5, H5,
  A6, B6, C6, D6, E6, F6, G6, H6,
  A7, B7, C7, D7, E7, F7, G7, H7,
  A8, B8, C8, D8, E8, F8, G8, H8,
  SQ_NONE = 64
};
constexpr int file_of(int sq) { return sq & 7; }
constexpr int rank_of(int sq) { return sq >> 3; }
constexpr Square make_square(int file, int rank) { return Square(rank * 8 + file); }
/** The same square seen from black's side (a1 <-> a8). */
constexpr Square flip(Square sq) { return Square(sq ^ 56); }
/** The square in front of {@code sq} for colour {@code c} ("up" for white, "down" for black). */
constexpr Square forward(Color c, Square sq) { return Square(c == WHITE ? sq + 8 : sq - 8); }

// Castling rights as 4 bits.
enum Castling : int { WHITE_OO = 1, WHITE_OOO = 2, BLACK_OO = 4, BLACK_OOO = 8, ALL_CASTLING = 15 };

// Move flags, as in v1 (phase1.MoveFlag).
enum MoveFlag : int {
  FLAG_NONE = 0,
  FLAG_EN_PASSANT = 1,
  FLAG_CASTLE = 2,
  FLAG_DOUBLE_PUSH = 3,
  FLAG_PROMO_QUEEN = 4,
  FLAG_PROMO_KNIGHT = 5,
  FLAG_PROMO_ROOK = 6,
  FLAG_PROMO_BISHOP = 7,
};

/** A move in 16 bits. 0 means "no move" (a1a1 is never legal). */
using Move = std::uint16_t;
constexpr Move MOVE_NONE = 0;
constexpr Move make_move(int from, int to, int flag = FLAG_NONE) {
  return Move(from | (to << 6) | (flag << 12));
}
constexpr Square move_from(Move m) { return Square(m & 63); }
constexpr Square move_to(Move m) { return Square((m >> 6) & 63); }
constexpr int move_flag(Move m) { return m >> 12; }
constexpr bool is_promotion(Move m) { return move_flag(m) >= FLAG_PROMO_QUEEN; }
constexpr PieceType promotion_type(Move m) {
  constexpr PieceType types[4] = {QUEEN, KNIGHT, ROOK, BISHOP};
  return types[move_flag(m) - FLAG_PROMO_QUEEN];
}

std::string square_name(Square sq);
/** UCI text of a move, e.g. "e2e4", "e7e8q". Castling is written as the king's move (e1g1). */
std::string move_to_uci(Move m);

}  // namespace v3
