#pragma once
// The UCI front door. Reads commands from a stream and answers on another (stdin/stdout for real
// use, string streams in the tests).
//
// "go" runs the search on a background thread, so this thread can still answer "isready" and
// act on "stop" while the engine thinks; the search prints "bestmove" when it ends. Output from
// both threads goes through one mutex so lines never interleave.
//
// Options: Threads (Lazy SMP search threads), Hash (MB), EvalFile (a .nnue path, or <embedded>),
// Move Overhead (ms kept in reserve per move for network lag), Features (search switches for A/B
// tests, see search.h).

#include <iosfwd>
#include <memory>
#include <mutex>
#include <string>
#include <thread>

#include "board/position.h"
#include "search/search.h"

namespace v3 {

class Uci {
 public:
  Uci();
  ~Uci();

  /** Runs until "quit" or end of input. */
  void loop(std::istream& in, std::ostream& out);
  /** Handles one command line; returns false on "quit". */
  bool command(const std::string& line, std::ostream& out);
  /** Waits for a running search to finish (tests, "go depth N" from the command line). */
  void wait();

  const Position& position() const { return pos_; }
  Engine& search() { return *search_; }

 private:
  void set_position(std::istringstream& args, std::ostream& out);
  void set_option(std::istringstream& args, std::ostream& out);
  void go(std::istringstream& args, std::ostream& out);
  void stop_search();
  void say(std::ostream& out, const std::string& line);
  bool load_network(const std::string& value, std::ostream& out);

  Position pos_;
  std::unique_ptr<Engine> search_;
  std::thread thread_;
  std::mutex out_mutex_;
  int move_overhead_ms_ = 30;
  std::string eval_file_ = "<embedded>";
  std::string net_description_;
};

/** Fixed-depth search over a fixed set of positions: total nodes (deterministic) and speed. */
void bench(std::ostream& out, int depth = 11);
/** The old step-1 workload: perft only. */
void bench_perft(std::ostream& out);

}  // namespace v3
