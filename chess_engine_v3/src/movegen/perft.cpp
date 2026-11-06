#include "movegen/perft.h"

#include "movegen/movegen.h"

namespace v3 {

std::uint64_t perft(Position& pos, int depth) {
  MoveList list;
  generate(pos, list);
  // The generator is fully legal, so at depth 1 the list size IS the count ("bulk counting").
  if (depth <= 1) return depth == 1 ? list.size : 1;
  std::uint64_t nodes = 0;
  for (Move m : list) {
    pos.make_move(m);
    nodes += perft(pos, depth - 1);
    pos.unmake_move(m);
  }
  return nodes;
}

std::uint64_t perft_divide(Position& pos, int depth, std::string& out) {
  MoveList list;
  generate(pos, list);
  std::uint64_t total = 0;
  for (Move m : list) {
    pos.make_move(m);
    std::uint64_t n = depth > 1 ? perft(pos, depth - 1) : 1;
    pos.unmake_move(m);
    out += move_to_uci(m) + ": " + std::to_string(n) + "\n";
    total += n;
  }
  return total;
}

}  // namespace v3
