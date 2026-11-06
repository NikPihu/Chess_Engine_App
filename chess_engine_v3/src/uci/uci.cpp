#include "uci/uci.h"

#include <algorithm>
#include <cctype>
#include <chrono>
#include <iostream>
#include <sstream>

#include "movegen/movegen.h"
#include "movegen/perft.h"

namespace v3 {

Uci::Uci() : search_(std::make_unique<Search>()) {
  std::ostringstream ignore;
  load_network("<embedded>", ignore);
}

Uci::~Uci() { stop_search(); }

void Uci::say(std::ostream& out, const std::string& line) {
  std::lock_guard<std::mutex> lock(out_mutex_);
  out << line << std::endl;
}

void Uci::loop(std::istream& in, std::ostream& out) {
  std::string line;
  while (std::getline(in, line)) {
    if (!line.empty() && line.back() == '\r') line.pop_back();
    if (!command(line, out)) break;
  }
  stop_search();
}

void Uci::wait() {
  if (thread_.joinable()) thread_.join();
}

void Uci::stop_search() {
  if (thread_.joinable()) {
    search_->stop();
    thread_.join();
  }
}

bool Uci::load_network(const std::string& value, std::ostream& out) {
  std::string error;
  std::shared_ptr<const nnue::Network> net =
      value.empty() || value == "<embedded>" ? nnue::load_embedded(error) : nnue::load(value, error);
  if (!net) {
    say(out, "info string EvalFile: " + error + (search_->has_network() ? " (keeping the current network)" : " (using material evaluation)"));
    return false;
  }
  search_->set_network(net);
  eval_file_ = value.empty() ? "<embedded>" : value;
  net_description_ = eval_file_;
  return true;
}

bool Uci::command(const std::string& line, std::ostream& out) {
  std::istringstream args(line);
  std::string cmd;
  if (!(args >> cmd)) return true;

  if (cmd == "uci") {
    std::lock_guard<std::mutex> lock(out_mutex_);
    out << "id name chess_engine_v3\nid author NikPihu\n"
        << "option name Hash type spin default 16 min 1 max 4096\n"
        << "option name EvalFile type string default <embedded>\n"
        << "option name Move Overhead type spin default 30 min 0 max 5000\n"
        << "option name Features type string default v3\n"
        << "uciok" << std::endl;
  } else if (cmd == "isready") {
    say(out, "readyok");
  } else if (cmd == "ucinewgame") {
    stop_search();
    search_->new_game();
    pos_.set_fen(Position::START_FEN);
  } else if (cmd == "setoption") {
    stop_search();
    set_option(args, out);
  } else if (cmd == "position") {
    stop_search();
    set_position(args, out);
  } else if (cmd == "go") {
    go(args, out);
  } else if (cmd == "stop") {
    stop_search();
  } else if (cmd == "perft") {
    int depth = 1;
    args >> depth;
    std::string text;
    auto start = std::chrono::steady_clock::now();
    std::uint64_t total = perft_divide(pos_, depth, text);
    double secs = std::chrono::duration<double>(std::chrono::steady_clock::now() - start).count();
    std::lock_guard<std::mutex> lock(out_mutex_);
    out << text << "\nNodes searched: " << total << "\nTime: " << int(secs * 1000) << " ms, "
        << std::uint64_t(total / std::max(secs, 1e-9)) << " nps" << std::endl;
  } else if (cmd == "d") {
    std::lock_guard<std::mutex> lock(out_mutex_);
    out << pos_.pretty() << std::flush;
  } else if (cmd == "eval") {
    say(out, "eval " + std::to_string(search_->evaluate_position(pos_)) + " cp (side to move), network " +
                 net_description_);
  } else if (cmd == "bench") {
    int depth = 11;
    args >> depth;
    bench(out, depth);
  } else if (cmd == "benchperft") {
    bench_perft(out);
  } else if (cmd == "quit") {
    stop_search();
    return false;
  } else {
    say(out, "info string unknown command: " + line);
  }
  return true;
}

void Uci::set_option(std::istringstream& args, std::ostream& out) {
  // setoption name <Name with spaces> value <value with spaces>
  std::string token, name, value;
  bool in_value = false;
  args >> token;  // "name"
  while (args >> token) {
    if (!in_value && token == "value") {
      in_value = true;
      continue;
    }
    std::string& target = in_value ? value : name;
    if (!target.empty()) target += ' ';
    target += token;
  }
  std::string key = name;
  std::transform(key.begin(), key.end(), key.begin(), [](unsigned char c) { return char(std::tolower(c)); });
  if (key == "hash") {
    search_->set_hash_mb(std::clamp(std::atoi(value.c_str()), 1, 4096));
  } else if (key == "evalfile") {
    load_network(value, out);
  } else if (key == "move overhead") {
    move_overhead_ms_ = std::clamp(std::atoi(value.c_str()), 0, 5000);
  } else if (key == "features") {
    std::uint32_t f;
    if (features::parse(value, f)) search_->set_features(f);
    else say(out, "info string Features: cannot parse '" + value + "'");
  } else {
    say(out, "info string unknown option '" + name + "'");
  }
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
    say(out, "info string invalid fen: " + fen);
    pos_.set_fen(Position::START_FEN);
    return;
  }
  while (args >> token) {
    Move m = parse_uci_move(pos_, token);
    if (m == MOVE_NONE) {
      say(out, "info string illegal move: " + token);
      return;
    }
    pos_.make_move(m);
  }
}

void Uci::go(std::istringstream& args, std::ostream& out) {
  stop_search();
  SearchLimits limits;
  std::string token;
  while (args >> token) {
    if (token == "perft") {
      int depth = 1;
      args >> depth;
      std::string text;
      std::uint64_t total = perft_divide(pos_, depth, text);
      std::lock_guard<std::mutex> lock(out_mutex_);
      out << text << "\nNodes searched: " << total << std::endl;
      return;
    }
    if (token == "wtime") args >> limits.time[WHITE];
    else if (token == "btime") args >> limits.time[BLACK];
    else if (token == "winc") args >> limits.inc[WHITE];
    else if (token == "binc") args >> limits.inc[BLACK];
    else if (token == "movestogo") args >> limits.moves_to_go;
    else if (token == "movetime") args >> limits.move_time;
    else if (token == "depth") args >> limits.depth;
    else if (token == "nodes") args >> limits.nodes;
    else if (token == "infinite") limits.infinite = true;
  }
  // Keep Move Overhead ms of each clock in reserve for network and GUI delays (as v1/v2 do).
  for (Color c : {WHITE, BLACK})
    if (limits.time[c] > 0) limits.time[c] = std::max<std::int64_t>(1, limits.time[c] - move_overhead_ms_);

  Position root = pos_;
  search_->clear_stop();
  thread_ = std::thread([this, root, limits, &out]() {
    SearchResult r = search_->think(root, limits, [this, &out](const std::string& s) { say(out, s); });
    say(out, "bestmove " + move_to_uci(r.best));
  });
}

void bench(std::ostream& out, int depth) {
  // A mix of openings, middlegames and endgames (the standard perft positions plus a few more).
  static const char* FENS[] = {
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
      "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
      "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
      "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
      "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
      "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
      "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
      "r2q1rk1/pp2bppp/2n1pn2/2pp4/3P4/2PBPN2/PP1N1PPP/R2QK2R w KQ - 0 9",
      "2r3k1/pp3ppp/4p3/3pP3/3P4/P1R5/1P3PPP/6K1 w - - 0 25",
      "8/5pk1/6p1/7p/3R3P/6P1/5PK1/1r6 w - - 0 40",
      "6k1/5p2/6p1/8/7p/8/6PP/6K1 b - - 0 1",
      "8/8/8/4k3/8/8/2K1P3/8 w - - 0 1",
  };
  std::uint64_t nodes = 0;
  std::int64_t start = now_ms();
  auto search = std::make_unique<Search>();
  std::string error;
  if (auto net = nnue::load_embedded(error)) search->set_network(net);
  for (const char* fen : FENS) {
    Position pos;
    pos.set_fen(fen);
    search->new_game();
    SearchLimits limits;
    limits.depth = depth;
    nodes += search->think(pos, limits, nullptr).nodes;
  }
  std::int64_t ms = std::max<std::int64_t>(1, now_ms() - start);
  out << nodes << " nodes " << nodes * 1000 / ms << " nps (depth " << depth << ", " << ms << " ms)"
      << std::endl;
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
