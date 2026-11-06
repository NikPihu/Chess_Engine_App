// chess_engine_v3 entry point.
//   engine_v3.exe            UCI mode (what GUIs, fastchess and lichess-bot use)
//   engine_v3.exe bench      fixed-depth search workload: node count (deterministic) + speed
//   engine_v3.exe "position startpos" "go depth 12"   commands as arguments, then exit
//   engine_v3.exe "perft 5"  any UCI command line(s) as arguments, then exits

#include <iostream>
#include <string>

#include "board/position.h"
#include "uci/uci.h"

int main(int argc, char** argv) {
  std::ios::sync_with_stdio(false);
  v3::init_all();
  v3::Uci uci;
  if (argc > 1) {
    for (int i = 1; i < argc; i++) {
      uci.command(argv[i], std::cout);
      uci.wait();  // let "go ..." finish before the next command
    }
    return 0;
  }
  uci.loop(std::cin, std::cout);
  return 0;
}
