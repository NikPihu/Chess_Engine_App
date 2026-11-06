#include "search/syzygy.h"

#include "movegen/movegen.h"
#include "tbprobe.h"

namespace v3::syzygy {

namespace {
int g_largest = 0;

bool probable(const Position& pos) {
  return g_largest > 0 && popcount(pos.pieces()) <= g_largest && pos.castling_rights() == 0;
}
}  // namespace

int init(const std::string& path) {
  if (path.empty() || path == "<empty>") {
    tb_free();
    g_largest = 0;
    return 0;
  }
  if (!tb_init(path.c_str())) {
    g_largest = 0;
    return 0;
  }
  g_largest = int(TB_LARGEST);
  return g_largest;
}

int largest() { return g_largest; }

bool probe_wdl(const Position& pos, int& wdl) {
  if (!probable(pos) || pos.rule50() != 0) return false;
  unsigned r = tb_probe_wdl(pos.pieces(WHITE), pos.pieces(BLACK), pos.pieces(KING), pos.pieces(QUEEN),
                            pos.pieces(ROOK), pos.pieces(BISHOP), pos.pieces(KNIGHT), pos.pieces(PAWN),
                            0, 0, pos.ep_square() == SQ_NONE ? 0 : unsigned(pos.ep_square()),
                            pos.side_to_move() == WHITE);
  if (r == TB_RESULT_FAILED) return false;
  wdl = int(r) - 2;  // TB_LOSS..TB_WIN = 0..4
  return true;
}

Move probe_root(const Position& pos, int& wdl) {
  if (!probable(pos)) return MOVE_NONE;
  unsigned r = tb_probe_root(pos.pieces(WHITE), pos.pieces(BLACK), pos.pieces(KING), pos.pieces(QUEEN),
                             pos.pieces(ROOK), pos.pieces(BISHOP), pos.pieces(KNIGHT), pos.pieces(PAWN),
                             unsigned(pos.rule50()), 0,
                             pos.ep_square() == SQ_NONE ? 0 : unsigned(pos.ep_square()),
                             pos.side_to_move() == WHITE, nullptr);
  if (r == TB_RESULT_FAILED || r == TB_RESULT_CHECKMATE || r == TB_RESULT_STALEMATE) return MOVE_NONE;
  wdl = int(TB_GET_WDL(r)) - 2;
  const Square from = Square(TB_GET_FROM(r)), to = Square(TB_GET_TO(r));
  const unsigned promo = TB_GET_PROMOTES(r);  // 0 none, 1 queen, 2 rook, 3 bishop, 4 knight
  static constexpr PieceType PROMO[5] = {PAWN, QUEEN, ROOK, BISHOP, KNIGHT};
  MoveList list;
  generate(pos, list);
  for (Move m : list) {
    if (move_from(m) != from || move_to(m) != to) continue;
    if (is_promotion(m) != (promo != 0)) continue;
    if (promo && promotion_type(m) != PROMO[promo]) continue;
    return m;
  }
  return MOVE_NONE;
}

}  // namespace v3::syzygy
