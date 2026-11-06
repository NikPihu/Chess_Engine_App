#pragma once
// The position: piece bitboards + a mailbox, side to move, castling, en passant, the 50-move counter
// and a history of per-move states so moves can be unmade and repetitions found.
//
// Bitboards answer set questions fast ("all white knights", "everything that attacks e4"); the
// mailbox (board[64]) answers "what is on this square" in one read. Both are kept in sync by
// put/remove/move_piece, and so is the Zobrist key, which is updated incrementally (XOR the piece in
// or out) instead of recomputed - the tests check the two always agree.

#include <string>
#include <vector>

#include "core/bitboard.h"
#include "core/types.h"

namespace v3 {

namespace zobrist {
extern Key piece_square[PIECE_NB][64];
extern Key castling[16];
extern Key ep_file[8];
extern Key side;
void init();
}  // namespace zobrist

/** Everything make_move changes that unmake_move cannot recompute. One per ply. */
struct StateInfo {
  Key key = 0;
  Key pawn_key = 0;  // pawns + kings only (for pawn-structure caches / correction history later)
  int castling = 0;
  Square ep = SQ_NONE;  // set only when an en passant capture is actually possible (pseudo-legally)
  int rule50 = 0;       // half-moves since the last capture or pawn move
  int plies_from_null = 0;
  Piece captured = NO_PIECE;
  Bitboard checkers = 0;
};

class Position {
 public:
  static constexpr const char* START_FEN =
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

  Position();
  /** Parses a FEN. Returns false (and leaves an empty-ish position) if it is malformed. */
  bool set_fen(const std::string& fen);
  std::string fen() const;
  /** Text diagram for the "d" command. */
  std::string pretty() const;

  void make_move(Move m);
  void unmake_move(Move m);
  void make_null();
  void unmake_null();

  Color side_to_move() const { return stm_; }
  Piece piece_on(Square sq) const { return board_[sq]; }
  Bitboard pieces() const { return by_color_[WHITE] | by_color_[BLACK]; }
  Bitboard pieces(Color c) const { return by_color_[c]; }
  Bitboard pieces(PieceType t) const { return by_type_[t]; }
  Bitboard pieces(Color c, PieceType t) const { return by_color_[c] & by_type_[t]; }
  Bitboard pieces(Color c, PieceType t1, PieceType t2) const {
    return by_color_[c] & (by_type_[t1] | by_type_[t2]);
  }
  Square king_square(Color c) const { return lsb(pieces(c, KING)); }

  const StateInfo& state() const { return states_.back(); }
  Key key() const { return state().key; }
  Key pawn_key() const { return state().pawn_key; }
  int castling_rights() const { return state().castling; }
  Square ep_square() const { return state().ep; }
  int rule50() const { return state().rule50; }
  Bitboard checkers() const { return state().checkers; }
  bool in_check() const { return state().checkers != 0; }
  Piece captured_piece() const { return state().captured; }
  int game_ply() const { return game_ply_; }

  /** All pieces of either colour attacking {@code sq}, given occupancy {@code occupied}. */
  Bitboard attackers_to(Square sq, Bitboard occupied) const;
  bool attacked_by(Color attacker, Square sq, Bitboard occupied) const;
  bool attacked_by(Color attacker, Square sq) const { return attacked_by(attacker, sq, pieces()); }

  /** Draw by 50-move rule or repetition (a single repetition inside the search counts). */
  bool is_draw(int ply_from_root) const;
  /** Insufficient material for either side to mate (K v K, K+minor v K). */
  bool insufficient_material() const;

  /** Recomputes the Zobrist key from scratch (the tests compare it with the incremental key). */
  Key compute_key() const;
  Key compute_pawn_key() const;

 private:
  void clear();
  void put(Piece p, Square sq);
  void remove(Square sq);
  void move_piece(Square from, Square to);
  Square ep_if_capturable(Square ep, Color capturer) const;

  Bitboard by_type_[PIECE_TYPE_NB] = {};
  Bitboard by_color_[2] = {};
  Piece board_[64];
  Color stm_ = WHITE;
  int game_ply_ = 0;
  std::vector<StateInfo> states_;
};

/** Square-by-square castling-right masks: moving from/to a square keeps only these rights. */
extern int castling_mask[64];

/** Must be called once before anything else (attack tables + Zobrist keys). */
void init_all();

}  // namespace v3
