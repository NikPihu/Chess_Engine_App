#include "board/position.h"

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <sstream>

namespace v3 {

namespace zobrist {
Key piece_square[PIECE_NB][64];
Key castling[16];
Key ep_file[8];
Key side;

void init() {
  // splitmix64 with a fixed seed: the same keys on every run, so bench node counts are stable.
  std::uint64_t s = 0x9E3779B97F4A7C15ULL ^ 20261106ULL;
  auto next = [&s]() {
    std::uint64_t z = (s += 0x9E3779B97F4A7C15ULL);
    z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ULL;
    z = (z ^ (z >> 27)) * 0x94D049BB133111EBULL;
    return z ^ (z >> 31);
  };
  for (auto& piece : piece_square)
    for (auto& k : piece) k = next();
  // Castling keys are XOR-combinations of 4 per-right keys, so any rights change is one XOR.
  Key right[4] = {next(), next(), next(), next()};
  for (int r = 0; r < 16; r++) {
    castling[r] = 0;
    for (int i = 0; i < 4; i++)
      if (r & (1 << i)) castling[r] ^= right[i];
  }
  for (auto& k : ep_file) k = next();
  side = next();
}
}  // namespace zobrist

int castling_mask[64];

void init_all() {
  static bool done = false;
  if (done) return;
  done = true;
  attacks::init();
  zobrist::init();
  for (int& m : castling_mask) m = ALL_CASTLING;
  castling_mask[E1] = ~(WHITE_OO | WHITE_OOO) & ALL_CASTLING;
  castling_mask[H1] = ~WHITE_OO & ALL_CASTLING;
  castling_mask[A1] = ~WHITE_OOO & ALL_CASTLING;
  castling_mask[E8] = ~(BLACK_OO | BLACK_OOO) & ALL_CASTLING;
  castling_mask[H8] = ~BLACK_OO & ALL_CASTLING;
  castling_mask[A8] = ~BLACK_OOO & ALL_CASTLING;
}

Position::Position() {
  init_all();
  states_.reserve(1024);
  set_fen(START_FEN);
}

void Position::clear() {
  std::memset(by_type_, 0, sizeof(by_type_));
  std::memset(by_color_, 0, sizeof(by_color_));
  for (auto& p : board_) p = NO_PIECE;
  stm_ = WHITE;
  game_ply_ = 0;
  states_.clear();
  states_.emplace_back();
}

void Position::put(Piece p, Square sq) {
  board_[sq] = p;
  by_type_[type_of(p)] |= bb(sq);
  by_color_[color_of(p)] |= bb(sq);
}

void Position::remove(Square sq) {
  Piece p = board_[sq];
  by_type_[type_of(p)] ^= bb(sq);
  by_color_[color_of(p)] ^= bb(sq);
  board_[sq] = NO_PIECE;
}

void Position::move_piece(Square from, Square to) {
  Piece p = board_[from];
  Bitboard fromTo = bb(from) | bb(to);
  by_type_[type_of(p)] ^= fromTo;
  by_color_[color_of(p)] ^= fromTo;
  board_[from] = NO_PIECE;
  board_[to] = p;
}

static constexpr const char* PIECE_CHARS = "PNBRQKpnbrqk";

bool Position::set_fen(const std::string& fen) {
  clear();
  std::istringstream in(fen);
  std::string placement, side, castle = "-", ep = "-";
  int rule50 = 0, fullmove = 1;
  if (!(in >> placement >> side)) return false;
  in >> castle >> ep >> rule50 >> fullmove;

  int rank = 7, file = 0;
  for (char c : placement) {
    if (c == '/') {
      if (file != 8 || rank == 0) return false;
      rank--;
      file = 0;
    } else if (c >= '1' && c <= '8') {
      file += c - '0';
    } else {
      const char* p = std::strchr(PIECE_CHARS, c);
      if (!p || file > 7) return false;
      put(Piece(p - PIECE_CHARS), make_square(file, rank));
      file++;
    }
    if (file > 8) return false;
  }
  if (rank != 0 || file != 8) return false;
  if (popcount(pieces(WHITE, KING)) != 1 || popcount(pieces(BLACK, KING)) != 1) return false;
  if (side != "w" && side != "b") return false;
  stm_ = side == "w" ? WHITE : BLACK;

  StateInfo& st = states_.back();
  for (char c : castle) {
    if (c == 'K' && piece_on(E1) == make_piece(WHITE, KING) && piece_on(H1) == make_piece(WHITE, ROOK)) st.castling |= WHITE_OO;
    if (c == 'Q' && piece_on(E1) == make_piece(WHITE, KING) && piece_on(A1) == make_piece(WHITE, ROOK)) st.castling |= WHITE_OOO;
    if (c == 'k' && piece_on(E8) == make_piece(BLACK, KING) && piece_on(H8) == make_piece(BLACK, ROOK)) st.castling |= BLACK_OO;
    if (c == 'q' && piece_on(E8) == make_piece(BLACK, KING) && piece_on(A8) == make_piece(BLACK, ROOK)) st.castling |= BLACK_OOO;
  }
  if (ep.size() == 2 && ep[0] >= 'a' && ep[0] <= 'h' && (ep[1] == '3' || ep[1] == '6')) {
    st.ep = ep_if_capturable(make_square(ep[0] - 'a', ep[1] - '1'), stm_);
  }
  st.rule50 = rule50;
  game_ply_ = 2 * (fullmove > 0 ? fullmove - 1 : 0) + (stm_ == BLACK);
  st.key = compute_key();
  st.pawn_key = compute_pawn_key();
  st.checkers = attackers_to(king_square(stm_), pieces()) & pieces(~stm_);
  // The side that just moved must not be in check.
  if (attacked_by(stm_, king_square(~stm_))) return false;
  return true;
}

std::string Position::fen() const {
  std::string s;
  for (int rank = 7; rank >= 0; rank--) {
    int empty = 0;
    for (int file = 0; file < 8; file++) {
      Piece p = board_[make_square(file, rank)];
      if (p == NO_PIECE) {
        empty++;
        continue;
      }
      if (empty) s += char('0' + empty);
      empty = 0;
      s += PIECE_CHARS[p];
    }
    if (empty) s += char('0' + empty);
    if (rank) s += '/';
  }
  s += stm_ == WHITE ? " w " : " b ";
  int c = castling_rights();
  if (!c) s += '-';
  if (c & WHITE_OO) s += 'K';
  if (c & WHITE_OOO) s += 'Q';
  if (c & BLACK_OO) s += 'k';
  if (c & BLACK_OOO) s += 'q';
  s += ' ' + square_name(ep_square()) + ' ' + std::to_string(rule50()) + ' ' +
       std::to_string(1 + game_ply_ / 2);
  return s;
}

std::string Position::pretty() const {
  std::string s = " +---+---+---+---+---+---+---+---+\n";
  for (int rank = 7; rank >= 0; rank--) {
    for (int file = 0; file < 8; file++) {
      Piece p = board_[make_square(file, rank)];
      s += " | ";
      s += p == NO_PIECE ? ' ' : PIECE_CHARS[p];
    }
    s += " | " + std::to_string(rank + 1) + "\n +---+---+---+---+---+---+---+---+\n";
  }
  s += "   a   b   c   d   e   f   g   h\n\nFen: " + fen() + "\nKey: ";
  char buf[20];
  std::snprintf(buf, sizeof buf, "%016llx", (unsigned long long)key());
  return s + buf + "\n";
}

Key Position::compute_key() const {
  Key k = 0;
  for (int sq = 0; sq < 64; sq++)
    if (board_[sq] != NO_PIECE) k ^= zobrist::piece_square[board_[sq]][sq];
  k ^= zobrist::castling[castling_rights()];
  if (ep_square() != SQ_NONE) k ^= zobrist::ep_file[file_of(ep_square())];
  if (stm_ == BLACK) k ^= zobrist::side;
  return k;
}

Key Position::compute_pawn_key() const {
  Key k = 0;
  Bitboard b = pieces(PAWN) | pieces(KING);
  while (b) {
    Square sq = pop_lsb(b);
    k ^= zobrist::piece_square[board_[sq]][sq];
  }
  return k;
}

Bitboard Position::attackers_to(Square sq, Bitboard occupied) const {
  return (attacks::pawn[BLACK][sq] & pieces(WHITE, PAWN)) |
         (attacks::pawn[WHITE][sq] & pieces(BLACK, PAWN)) |
         (attacks::knight[sq] & pieces(KNIGHT)) | (attacks::king[sq] & pieces(KING)) |
         (attacks::bishop(sq, occupied) & (pieces(BISHOP) | pieces(QUEEN))) |
         (attacks::rook(sq, occupied) & (pieces(ROOK) | pieces(QUEEN)));
}

bool Position::attacked_by(Color c, Square sq, Bitboard occupied) const {
  return (attacks::pawn[~c][sq] & pieces(c, PAWN)) || (attacks::knight[sq] & pieces(c, KNIGHT)) ||
         (attacks::king[sq] & pieces(c, KING)) ||
         (attacks::bishop(sq, occupied) & pieces(c, BISHOP, QUEEN)) ||
         (attacks::rook(sq, occupied) & pieces(c, ROOK, QUEEN));
}

/** Keep an en passant square only if a pawn of {@code capturer} could take there. Otherwise two
 *  identical positions would get different keys and repetitions would be missed. */
Square Position::ep_if_capturable(Square ep, Color capturer) const {
  return (attacks::pawn[~capturer][ep] & pieces(capturer, PAWN)) ? ep : SQ_NONE;
}

void Position::make_move(Move m) {
  const Square from = move_from(m), to = move_to(m);
  const int flag = move_flag(m);
  const Color us = stm_, them = ~us;
  const Piece piece = board_[from];

  states_.push_back(states_.back());
  StateInfo& st = states_.back();
  Key key = st.key ^ zobrist::side;
  if (st.ep != SQ_NONE) key ^= zobrist::ep_file[file_of(st.ep)];
  st.ep = SQ_NONE;
  st.rule50++;
  st.plies_from_null++;
  st.captured = NO_PIECE;

  if (flag == FLAG_CASTLE) {
    // The move is the king's (e1g1 / e1c1); the rook jumps alongside.
    bool kingside = to > from;
    Square rook_from = make_square(kingside ? 7 : 0, rank_of(from));
    Square rook_to = make_square(kingside ? 5 : 3, rank_of(from));
    Piece rook = board_[rook_from];
    move_piece(rook_from, rook_to);
    key ^= zobrist::piece_square[rook][rook_from] ^ zobrist::piece_square[rook][rook_to];
  } else {
    Square cap_sq = flag == FLAG_EN_PASSANT ? Square(to + (us == WHITE ? -8 : 8)) : to;
    Piece captured = board_[cap_sq];
    if (captured != NO_PIECE) {
      remove(cap_sq);
      key ^= zobrist::piece_square[captured][cap_sq];
      if (type_of(captured) == PAWN) st.pawn_key ^= zobrist::piece_square[captured][cap_sq];
      st.captured = captured;
      st.rule50 = 0;
    }
  }

  move_piece(from, to);
  key ^= zobrist::piece_square[piece][from] ^ zobrist::piece_square[piece][to];
  if (type_of(piece) == PAWN || type_of(piece) == KING)
    st.pawn_key ^= zobrist::piece_square[piece][from] ^ zobrist::piece_square[piece][to];

  if (type_of(piece) == PAWN) {
    st.rule50 = 0;
    if (is_promotion(m)) {
      Piece promoted = make_piece(us, promotion_type(m));
      remove(to);
      put(promoted, to);
      key ^= zobrist::piece_square[piece][to] ^ zobrist::piece_square[promoted][to];
      st.pawn_key ^= zobrist::piece_square[piece][to];
    } else if (flag == FLAG_DOUBLE_PUSH) {
      Square ep = Square((from + to) / 2);
      // Check against the opponent's pawns: they are the ones who could capture.
      Bitboard their_pawns = pieces(them, PAWN);
      if (attacks::pawn[us][ep] & their_pawns) {
        st.ep = ep;
        key ^= zobrist::ep_file[file_of(ep)];
      }
    }
  }

  int rights = st.castling & castling_mask[from] & castling_mask[to];
  if (rights != st.castling) {
    key ^= zobrist::castling[st.castling] ^ zobrist::castling[rights];
    st.castling = rights;
  }

  stm_ = them;
  game_ply_++;
  st.key = key;
  st.checkers = attackers_to(king_square(them), pieces()) & pieces(us);
}

void Position::unmake_move(Move m) {
  const Square from = move_from(m), to = move_to(m);
  const int flag = move_flag(m);
  stm_ = ~stm_;
  game_ply_--;
  const Color us = stm_;
  const StateInfo& st = states_.back();

  if (is_promotion(m)) {
    remove(to);
    put(make_piece(us, PAWN), to);
  }
  move_piece(to, from);
  if (flag == FLAG_CASTLE) {
    bool kingside = to > from;
    move_piece(make_square(kingside ? 5 : 3, rank_of(from)),
               make_square(kingside ? 7 : 0, rank_of(from)));
  } else if (st.captured != NO_PIECE) {
    Square cap_sq = flag == FLAG_EN_PASSANT ? Square(to + (us == WHITE ? -8 : 8)) : to;
    put(st.captured, cap_sq);
  }
  states_.pop_back();
}

void Position::make_null() {
  states_.push_back(states_.back());
  StateInfo& st = states_.back();
  st.key ^= zobrist::side;
  if (st.ep != SQ_NONE) st.key ^= zobrist::ep_file[file_of(st.ep)];
  st.ep = SQ_NONE;
  st.rule50++;
  st.plies_from_null = 0;
  st.captured = NO_PIECE;
  stm_ = ~stm_;
  game_ply_++;
  st.checkers = 0;  // null move is never played in check
}

void Position::unmake_null() {
  stm_ = ~stm_;
  game_ply_--;
  states_.pop_back();
}

bool Position::is_draw(int ply_from_root) const {
  const StateInfo& st = state();
  if (st.rule50 >= 100) {
    // A checkmate on the 100th half-move still wins; the caller handles "no legal moves" first
    // in the search, so here we only refuse to call it a draw while in check.
    if (!st.checkers) return true;
  }
  // Repetition: only positions with the same side to move (every 2 plies) and since the last
  // irreversible move (rule50) or null move can repeat.
  int limit = std::min(st.rule50, st.plies_from_null);
  int n = int(states_.size()) - 1;
  int count = 0;
  for (int back = 4; back <= limit && n - back >= 0; back += 2) {
    if (states_[n - back].key == st.key) {
      // Inside the search tree one repetition is enough (the side to move can always repeat);
      // a repetition of a game position before the root needs a second one (real threefold).
      if (back < ply_from_root) return true;
      if (++count == 2) return true;
    }
  }
  return insufficient_material();
}

bool Position::insufficient_material() const {
  if (pieces(PAWN) | pieces(ROOK) | pieces(QUEEN)) return false;
  return popcount(pieces()) <= 3;  // K v K, K+N v K, K+B v K
}

}  // namespace v3
