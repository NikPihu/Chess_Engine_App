#pragma once
// The UCI loop: reads commands from a stream, answers on another (stdin/stdout for real use, string
// streams in the tests). At this stage (P1) it knows the board commands and perft; the search is
// added in P2.

#include <iosfwd>
#include <string>

#include "board/position.h"

namespace v3 {

class Uci {
 public:
  /** Runs until "quit" or end of input. */
  void loop(std::istream& in, std::ostream& out);
  /** Handles one command line; returns false on "quit". */
  bool command(const std::string& line, std::ostream& out);

  const Position& position() const { return pos_; }

 private:
  void set_position(std::istringstream& args, std::ostream& out);
  void go(std::istringstream& args, std::ostream& out);

  Position pos_;
};

/** Fixed perft workload: prints nodes and speed. The node total doubles as a correctness check. */
void bench_perft(std::ostream& out);

}  // namespace v3
