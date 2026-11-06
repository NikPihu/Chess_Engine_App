#include "movegen/movegen.h"

namespace v3 {

bool MoveList::contains(Move m) const {
  for (Move x : *this)
    if (x == m) return true;
  return false;
}

namespace {

void add_promotions(MoveList& list, Square from, Square to) {
  list.add(make_move(from, to, FLAG_PROMO_QUEEN));
  list.add(make_move(from, to, FLAG_PROMO_KNIGHT));
  list.add(make_move(from, to, FLAG_PROMO_ROOK));
  list.add(make_move(from, to, FLAG_PROMO_BISHOP));
}

/** Pinned pieces of {@code us}: our piece alone between our king and an enemy slider. */
Bitboard pinned_pieces(const Position& pos, Color us, Square ksq) {
  const Color them = ~us;
  Bitboard snipers = (attacks::rook(ksq, 0) & pos.pieces(them, ROOK, QUEEN)) |
                     (attacks::bishop(ksq, 0) & pos.pieces(them, BISHOP, QUEEN));
  Bitboard occupied = pos.pieces();
  Bitboard pinned = 0;
  while (snipers) {
    Square s = pop_lsb(snipers);
    Bitboard blockers = attacks::between[ksq][s] & occupied;
    if (blockers && !more_than_one(blockers)) pinned |= blockers & pos.pieces(us);
  }
  return pinned;
}

bool en_passant_legal(const Position& pos, Color us, Square ksq, Square from, Square ep) {
  const Color them = ~us;
  Square captured = Square(ep + (us == WHITE ? -8 : 8));
  Bitboard occupied = (pos.pieces() ^ bb(from) ^ bb(captured)) | bb(ep);
  Bitboard theirs = pos.pieces(them) & ~bb(captured);
  return !(attacks::bishop(ksq, occupied) & theirs & (pos.pieces(BISHOP) | pos.pieces(QUEEN))) &&
         !(attacks::rook(ksq, occupied) & theirs & (pos.pieces(ROOK) | pos.pieces(QUEEN))) &&
         !(attacks::knight[ksq] & theirs & pos.pieces(KNIGHT)) &&
         !(attacks::pawn[us][ksq] & theirs & pos.pieces(PAWN));
}

}  // namespace

void generate(const Position& pos, MoveList& list, GenType type) {
  const Color us = pos.side_to_move(), them = ~us;
  const Square ksq = pos.king_square(us);
  const Bitboard ours = pos.pieces(us), theirs = pos.pieces(them);
  const Bitboard occupied = ours | theirs;
  const Bitboard checkers = pos.checkers();

  // Which target squares this call wants: captures, empty squares, or both.
  const Bitboard want = type == ALL ? ~ours : type == NOISY ? theirs : ~occupied;

  // King moves: the king must not land on an attacked square; take it off the board first so a
  // slider checking along a line also covers the square behind the king.
  {
    Bitboard targets = attacks::king[ksq] & want;
    Bitboard without_king = occupied ^ bb(ksq);
    while (targets) {
      Square to = pop_lsb(targets);
      if (!pos.attacked_by(them, to, without_king)) list.add(make_move(ksq, to));
    }
  }
  if (more_than_one(checkers)) return;  // double check: only the king can move

  const Bitboard check_mask =
      checkers ? attacks::between[ksq][lsb(checkers)] | checkers : ~Bitboard(0);
  const Bitboard pinned = pinned_pieces(pos, us, ksq);

  // Knights, bishops, rooks, queens.
  for (PieceType pt : {KNIGHT, BISHOP, ROOK, QUEEN}) {
    Bitboard from_set = pos.pieces(us, pt);
    if (pt == KNIGHT) from_set &= ~pinned;  // a pinned knight can never move
    while (from_set) {
      Square from = pop_lsb(from_set);
      Bitboard targets = attacks::of(pt, from, occupied) & want & check_mask;
      if (pinned & bb(from)) targets &= attacks::line[ksq][from];
      while (targets) list.add(make_move(from, pop_lsb(targets)));
    }
  }

  // Pawns.
  const int up = us == WHITE ? 8 : -8;
  const Bitboard promo_rank = us == WHITE ? RANK_8 : RANK_1;
  const Bitboard start_rank = us == WHITE ? RANK_2 : RANK_7;
  const Square ep = pos.ep_square();
  Bitboard pawns = pos.pieces(us, PAWN);
  while (pawns) {
    Square from = pop_lsb(pawns);
    Bitboard allowed = check_mask;
    if (pinned & bb(from)) allowed &= attacks::line[ksq][from];

    // Pushes.
    Square one = Square(from + up);
    if (!(occupied & bb(one))) {
      if (bb(one) & promo_rank) {
        if (type != QUIET && (bb(one) & allowed)) add_promotions(list, from, one);
      } else if (type != NOISY) {
        if (bb(one) & allowed) list.add(make_move(from, one));
        Square two = Square(one + up);
        if ((bb(from) & start_rank) && !(occupied & bb(two)) && (bb(two) & allowed))
          list.add(make_move(from, two, FLAG_DOUBLE_PUSH));
      }
    }

    // Captures.
    if (type != QUIET) {
      Bitboard caps = attacks::pawn[us][from] & theirs & allowed;
      while (caps) {
        Square to = pop_lsb(caps);
        if (bb(to) & promo_rank)
          add_promotions(list, from, to);
        else
          list.add(make_move(from, to));
      }
      if (ep != SQ_NONE && (attacks::pawn[us][from] & bb(ep)) &&
          en_passant_legal(pos, us, ksq, from, ep))
        list.add(make_move(from, ep, FLAG_EN_PASSANT));
    }
  }

  // Castling: not out of, through, or into check; the squares between king and rook empty.
  if (type != NOISY && !checkers) {
    const int rights = pos.castling_rights();
    const int rank = us == WHITE ? 0 : 7;
    const Square e = make_square(4, rank);
    if (rights & (us == WHITE ? WHITE_OO : BLACK_OO)) {
      Square f = make_square(5, rank), g = make_square(6, rank);
      if (!(occupied & (bb(f) | bb(g))) && !pos.attacked_by(them, f) && !pos.attacked_by(them, g))
        list.add(make_move(e, g, FLAG_CASTLE));
    }
    if (rights & (us == WHITE ? WHITE_OOO : BLACK_OOO)) {
      Square d = make_square(3, rank), c = make_square(2, rank), b = make_square(1, rank);
      if (!(occupied & (bb(d) | bb(c) | bb(b))) && !pos.attacked_by(them, d) &&
          !pos.attacked_by(them, c))
        list.add(make_move(e, c, FLAG_CASTLE));
    }
  }
}

Move parse_uci_move(const Position& pos, const std::string& text) {
  MoveList list;
  generate(pos, list);
  for (Move m : list)
    if (move_to_uci(m) == text) return m;
  return MOVE_NONE;
}

}  // namespace v3
