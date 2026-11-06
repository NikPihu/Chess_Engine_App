#include "uci/uci.h"

#include <algorithm>
#include <chrono>
#include <iostream>
#include <sstream>

#include "movegen/movegen.h"
#include "movegen/perft.h"

namespace v3 {

void Uci::loop(std::istream& in, std::ostream& out) {
  std::string line;
  while (std::getline(in, line)) {
    if (!line.empty() && line.back() == '\r') line.pop_back();
    if (!command(line, out)) break;
  }
}

bool Uci::command(const std::string& line, std::ostream& out) {
  std::istringstream args(line);
  std::string cmd;
  if (!(args >> cmd)) return true;

  if (cmd == "uci") {
    out << "id name chess_engine_v3\nid author NikPihu\nuciok" << std::endl;
  } else if (cmd == "isready") {
    out << "readyok" << std::endl;
  } else if (cmd == "ucinewgame") {
    pos_.set_fen(Position::START_FEN);
  } else if (cmd == "position") {
    set_position(args, out);
  } else if (cmd == "go") {
    go(args, out);
  } else if (cmd == "perft") {
    int depth = 1;
    args >> depth;
    std::string text;
    auto start = std::chrono::steady_clock::now();
    std::uint64_t total = perft_divide(pos_, depth, text);
    double secs = std::chrono::duration<double>(std::chrono::steady_clock::now() - start).count();
    out << text << "\nNodes searched: " << total << "\n"
        << "Time: " << int(secs * 1000) << " ms, " << std::uint64_t(total / std::max(secs, 1e-9))
        << " nps" << std::endl;
  } else if (cmd == "d") {
    out << pos_.pretty() << std::flush;
  } else if (cmd == "bench") {
    bench_perft(out);
  } else if (cmd == "quit") {
    return false;
  } else {
    out << "info string unknown command: " << line << std::endl;
  }
  return true;
}

void Uci::set_position(std::istringstream& args, std::ostream& out) {
  std::string token, fen;
  args >> token;
  if (token == "startpos") {
    fen = Position::START_FEN;
    args >> token;  // "moves" or nothing
  } else if (token == "fen") {
    while (args >> token && token != "moves") fen += token + " ";
  } else {
    return;
  }
  if (!pos_.set_fen(fen)) {
    out << "info string invalid fen: " << fen << std::endl;
    pos_.set_fen(Position::START_FEN);
    return;
  }
  while (args >> token) {
    Move m = parse_uci_move(pos_, token);
    if (m == MOVE_NONE) {
      out << "info string illegal move: " << token << std::endl;
      return;
    }
    pos_.make_move(m);
  }
}

void Uci::go(std::istringstream& args, std::ostream& out) {
  std::string token;
  while (args >> token) {
    if (token == "perft") {
      int depth = 1;
      args >> depth;
      std::string text;
      std::uint64_t total = perft_divide(pos_, depth, text);
      out << text << "\nNodes searched: " << total << std::endl;
      return;
    }
  }
  // No search yet (P2): answer with the first legal move so GUIs don't hang.
  MoveList list;
  generate(pos_, list);
  out << "bestmove " << move_to_uci(list.size ? list.moves[0] : MOVE_NONE) << std::endl;
}

void bench_perft(std::ostream& out) {
  struct Case {
    const char* fen;
    int depth;
  };
  const Case cases[] = {
      {Position::START_FEN, 5},
      {"r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 4},
      {"8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 5},
      {"r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 4},
      {"rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 4},
  };
  std::uint64_t total = 0;
  auto start = std::chrono::steady_clock::now();
  for (const Case& c : cases) {
    Position pos;
    pos.set_fen(c.fen);
    total += perft(pos, c.depth);
  }
  double secs = std::chrono::duration<double>(std::chrono::steady_clock::now() - start).count();
  out << total << " nodes " << std::uint64_t(total / std::max(secs, 1e-9)) << " nps" << std::endl;
}

}  // namespace v3
