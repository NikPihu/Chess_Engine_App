// chess_engine_v3 tests. A tiny self-contained runner (no framework to install): each TEST is a
// function; CHECK records a failure with file:line and keeps going. Exit code 0 = all passed.
//
//   chess_engine_v3/bin/v3_tests.exe           all tests
//   chess_engine_v3/bin/v3_tests.exe perft     only tests whose name contains "perft"

#include <cstdio>
#include <cstring>
#include <functional>
#include <random>
#include <sstream>
#include <string>
#include <vector>

#include "board/position.h"
#include "movegen/movegen.h"
#include "movegen/perft.h"
#include "uci/uci.h"

using namespace v3;

namespace {

struct TestCase {
  const char* name;
  std::function<void()> fn;
};
std::vector<TestCase>& registry() {
  static std::vector<TestCase> r;
  return r;
}
int failures = 0;

struct Register {
  Register(const char* name, std::function<void()> fn) { registry().push_back({name, fn}); }
};

#define TEST(name)                                \
  static void name();                             \
  static Register reg_##name(#name, name);        \
  static void name()

#define CHECK(cond)                                                              \
  do {                                                                           \
    if (!(cond)) {                                                               \
      std::printf("  FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);              \
      failures++;                                                                \
    }                                                                            \
  } while (0)

#define CHECK_EQ(a, b)                                                                    \
  do {                                                                                    \
    auto va = (a);                                                                        \
    auto vb = (b);                                                                        \
    if (!(va == vb)) {                                                                    \
      std::ostringstream os;                                                              \
      os << va << " != " << vb;                                                           \
      std::printf("  FAIL %s:%d: %s == %s (%s)\n", __FILE__, __LINE__, #a, #b, os.str().c_str()); \
      failures++;                                                                         \
    }                                                                                     \
  } while (0)

// Same suite as chess_engine_v1/phase2/tests/PerftTest.java (chessprogramming.org positions 1-6
// plus the classic special cases). v3 must produce exactly v1's numbers.
struct PerftCase {
  const char* fen;
  int depth;
  std::uint64_t nodes;
};
const PerftCase PERFT_SUITE[] = {
    {"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", 5, 4865609},
    {"r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 4, 4085603},
    {"8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 5, 674624},
    {"r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 4, 422333},
    {"r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1", 4, 422333},
    {"rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 4, 2103487},
    {"r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10", 4, 3894594},
    {"3k4/3p4/8/K1P4r/8/8/8/8 b - - 0 1", 6, 1134888},
    {"8/8/4k3/8/2p5/8/B2P2K1/8 w - - 0 1", 6, 1015133},
    {"8/8/1k6/2b5/2pP4/8/5K2/8 b - d3 0 1", 6, 1440467},
    {"5k2/8/8/8/8/8/8/4K2R w K - 0 1", 6, 661072},
    {"3k4/8/8/8/8/8/8/R3K3 w Q - 0 1", 6, 803711},
    {"r3k2r/1b4bq/8/8/8/8/7B/R3K2R w KQkq - 0 1", 4, 1274206},
    {"r3k2r/8/3Q4/8/8/5q2/8/R3K2R b KQkq - 0 1", 4, 1720476},
    {"2K2r2/4P3/8/8/8/8/8/3k4 w - - 0 1", 6, 3821001},
    {"8/8/1P2K3/8/2n5/1q6/8/5k2 b - - 0 1", 5, 1004658},
    {"4k3/1P6/8/8/8/8/K7/8 w - - 0 1", 6, 217342},
    {"8/P1k5/K7/8/8/8/8/8 w - - 0 1", 6, 92683},
    {"K1k5/8/P7/8/8/8/8/8 w - - 0 1", 6, 2217},
    {"8/k1P5/8/1K6/8/8/8/8 w - - 0 1", 7, 567584},
    {"8/8/2k5/5q2/5n2/8/5K2/8 b - - 0 1", 4, 23527},
};

}  // namespace

TEST(slider_tables_match_slow_ray_walk) {
  std::mt19937_64 rng(7);
  for (int i = 0; i < 20000; i++) {
    Square sq = Square(rng() % 64);
    Bitboard occ = rng() & rng();
    CHECK_EQ(attacks::rook(sq, occ), attacks::slider_slow(ROOK, sq, occ));
    CHECK_EQ(attacks::bishop(sq, occ), attacks::slider_slow(BISHOP, sq, occ));
  }
}

TEST(between_and_line) {
  CHECK_EQ(attacks::between[A1][D1], bb(B1) | bb(C1));
  CHECK_EQ(attacks::between[A1][D4], bb(B2) | bb(C3));
  CHECK_EQ(attacks::between[A1][B3], Bitboard(0));
  CHECK_EQ(attacks::line[A1][C3] & bb(H8), bb(H8));
  CHECK_EQ(attacks::line[A1][B3], Bitboard(0));
}

TEST(fen_round_trip) {
  for (const PerftCase& c : PERFT_SUITE) {
    Position pos;
    CHECK(pos.set_fen(c.fen));
    CHECK_EQ(pos.fen(), std::string(c.fen));
  }
  Position pos;
  CHECK(!pos.set_fen("not a fen"));
  CHECK(!pos.set_fen("8/8/8/8/8/8/8/8 w - - 0 1"));  // no kings
}

TEST(perft_suite_matches_v1) {
  for (const PerftCase& c : PERFT_SUITE) {
    Position pos;
    pos.set_fen(c.fen);
    Key key = pos.key();
    std::uint64_t n = perft(pos, c.depth);
    if (n != c.nodes) std::printf("  perft %s depth %d\n", c.fen, c.depth);
    CHECK_EQ(n, c.nodes);
    CHECK_EQ(pos.fen(), std::string(c.fen));  // make/unmake restored everything
    CHECK_EQ(pos.key(), key);
  }
}

TEST(perft_startpos_depth6) {
  Position pos;
  CHECK_EQ(perft(pos, 6), std::uint64_t(119060324));
}

TEST(incremental_keys_and_generator_split_on_random_games) {
  std::mt19937_64 rng(42);
  for (const PerftCase& c : PERFT_SUITE) {
    for (int game = 0; game < 20; game++) {
      Position pos;
      pos.set_fen(c.fen);
      std::vector<Move> played;
      for (int ply = 0; ply < 150; ply++) {
        MoveList all, noisy, quiet;
        generate(pos, all, ALL);
        generate(pos, noisy, NOISY);
        generate(pos, quiet, QUIET);
        CHECK_EQ(all.size, noisy.size + quiet.size);
        for (Move m : noisy) CHECK(all.contains(m));
        for (Move m : quiet) CHECK(all.contains(m));
        if (all.size == 0) break;
        Move m = all.moves[rng() % all.size];
        pos.make_move(m);
        played.push_back(m);
        CHECK_EQ(pos.key(), pos.compute_key());
        CHECK_EQ(pos.pawn_key(), pos.compute_pawn_key());
        // The FEN printed and re-read gives the same key (ep square handling agrees).
        Position copy;
        CHECK(copy.set_fen(pos.fen()));
        CHECK_EQ(copy.key(), pos.key());
      }
      while (!played.empty()) {
        pos.unmake_move(played.back());
        played.pop_back();
      }
      CHECK_EQ(pos.fen(), std::string(c.fen));
    }
  }
}

TEST(repetition_and_fifty_move_draws) {
  Position pos;
  const char* moves[] = {"g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8"};
  for (int i = 0; i < 8; i++) {
    pos.make_move(parse_uci_move(pos, moves[i]));
    if (i == 3) {
      CHECK(!pos.is_draw(0));  // once repeated, before the root: not yet a draw
      CHECK(pos.is_draw(5));   // ...but inside the search one repetition is enough
    }
  }
  CHECK(pos.is_draw(0));  // threefold

  Position fifty;
  fifty.set_fen("4k3/8/8/8/8/8/8/4K2R w - - 99 80");
  CHECK(!fifty.is_draw(0));
  fifty.make_move(parse_uci_move(fifty, "h1h2"));
  CHECK(fifty.is_draw(0));

  Position bare;
  bare.set_fen("4k3/8/8/8/8/8/8/4KN2 w - - 0 1");
  CHECK(bare.is_draw(0));
}

TEST(uci_position_and_moves) {
  Uci uci;
  std::ostringstream out;
  uci.command("position startpos moves e2e4 e7e5 g1f3", out);
  CHECK_EQ(uci.position().fen(),
           std::string("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2"));
  uci.command("position fen 8/8/1k6/2b5/2pP4/8/5K2/8 b - d3 0 1 moves c4d3", out);
  CHECK_EQ(uci.position().fen(), std::string("8/8/1k6/2b5/8/3p4/5K2/8 w - - 0 2"));
  std::ostringstream hello;
  uci.command("uci", hello);
  CHECK(hello.str().find("uciok") != std::string::npos);
}

int main(int argc, char** argv) {
  init_all();
  const char* filter = argc > 1 ? argv[1] : "";
  int run = 0;
  for (const TestCase& t : registry()) {
    if (!std::strstr(t.name, filter)) continue;
    int before = failures;
    std::printf("%s\n", t.name);
    t.fn();
    run++;
    if (failures != before) std::printf("  -> %d failure(s)\n", failures - before);
  }
  std::printf("\n%d test(s), %s\n", run, failures ? "FAILED" : "all passed");
  return failures ? 1 : 0;
}
