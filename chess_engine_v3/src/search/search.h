#pragma once
// The search. Step 2 (v3.0) is a faithful port of v2's search (chess_engine_v2/search/NnueSearch =
// v1's phase6.Search2 with the SPRT-proven features, NNUE evaluation):
//   iterative deepening + aspiration windows, negamax alpha-beta with PVS, transposition table,
//   reverse futility pruning, null-move pruning, late move reductions, killers + history ordering,
//   quiescence search on captures and queen promotions, mate-distance pruning.
// Every feature has a bit in Features so P3 can switch each new idea on/off for its SPRT.

#include <atomic>
#include <cstdint>
#include <functional>
#include <memory>
#include <string>

#include "board/position.h"
#include "movegen/movegen.h"
#include "nnue/nnue.h"
#include "search/tt.h"

namespace v3 {

namespace features {
constexpr std::uint32_t KILLERS = 1u << 0;
constexpr std::uint32_t HISTORY = 1u << 1;
constexpr std::uint32_t PVS = 1u << 2;
constexpr std::uint32_t NULL_MOVE = 1u << 3;
constexpr std::uint32_t LMR = 1u << 4;
constexpr std::uint32_t CHECK_EXTENSION = 1u << 5;
constexpr std::uint32_t ASPIRATION = 1u << 6;
constexpr std::uint32_t REVERSE_FUTILITY = 1u << 7;
/** What v2 plays with (v1's Search2.PROVEN): everything above except check extensions. */
constexpr std::uint32_t PROVEN = KILLERS | HISTORY | PVS | NULL_MOVE | LMR | ASPIRATION | REVERSE_FUTILITY;
/** Parses "killers,history,pvs,nmp,lmr,check,aspiration,rfp" / "all" / "none" / "all,-nmp". */
bool parse(const std::string& text, std::uint32_t& out);
}  // namespace features

struct SearchLimits {
  std::int64_t time[2] = {-1, -1};  // wtime / btime in ms (-1 = no clock)
  std::int64_t inc[2] = {0, 0};
  int moves_to_go = 0;
  std::int64_t move_time = -1;  // ms
  int depth = -1;
  std::uint64_t nodes = 0;  // 0 = no node limit
  bool infinite = false;
  bool has_clock() const { return time[WHITE] >= 0 || time[BLACK] >= 0; }
};

/** v1's phase4.TimeManager: soft = remaining/30 + 3/4 increment, hard = 3x soft (max half clock). */
class TimeManager {
 public:
  TimeManager(const SearchLimits& limits, Color stm);
  std::int64_t hard_ms() const { return hard_; }
  bool stop_after_iteration(std::int64_t elapsed_ms) const { return clock_ && elapsed_ms >= soft_ / 2; }

 private:
  std::int64_t soft_ = -1, hard_ = -1;
  bool clock_ = false;
};

struct SearchResult {
  Move best = MOVE_NONE;
  int score = 0;
  int depth = 0;
  std::uint64_t nodes = 0;
};

class Search {
 public:
  using InfoFn = std::function<void(const std::string&)>;

  Search();

  void set_network(std::shared_ptr<const nnue::Network> net) { net_ = std::move(net); }
  bool has_network() const { return net_ != nullptr; }
  void set_hash_mb(int mb) { tt_.resize(mb); }
  void set_features(std::uint32_t f) { features_ = f; }
  std::uint32_t features() const { return features_; }
  void new_game();

  /** Searches {@code root} within {@code limits}; prints UCI "info" lines through {@code info}. */
  SearchResult think(const Position& root, const SearchLimits& limits, const InfoFn& info);
  /** Can be called from another thread (UCI "stop"). */
  void stop() { stop_.store(true, std::memory_order_relaxed); }
  /** Called before starting a new search (not inside think(), so an early "stop" is never lost). */
  void clear_stop() { stop_.store(false, std::memory_order_relaxed); }

  /** Static evaluation of a position (NNUE, or material if no network): the "eval" command. */
  int evaluate_position(const Position& pos);

 private:
  int root_search(int depth);
  int search(int depth, int ply, int alpha, int beta, bool allow_null);
  int quiescence(int ply, int alpha, int beta);
  int evaluate(int ply);
  void make(Move m, int ply);
  void make_null(int ply);
  void score_moves(const MoveList& list, int* scores, Move hash_move, int ply, bool quiescence);
  void update_pv(int ply, Move m);
  void check_limits();
  bool on(std::uint32_t f) const { return (features_ & f) != 0; }
  std::string info_line(int depth, int score, std::int64_t elapsed_ms) const;

  Position pos_;
  std::shared_ptr<const nnue::Network> net_;
  TranspositionTable tt_{16};
  std::uint32_t features_ = features::PROVEN;

  nnue::Accumulator acc_[MAX_PLY + 2];
  MoveList lists_[MAX_PLY + 1];
  int scores_[MAX_PLY + 1][256];
  Move pv_[MAX_PLY + 1][MAX_PLY + 1];
  int pv_length_[MAX_PLY + 1];
  Move killers_[MAX_PLY + 1][2];
  int history_[2][64][64];

  std::atomic<bool> stop_{false};
  bool aborted_ = false;
  std::uint64_t nodes_ = 0;
  std::uint64_t node_limit_ = 0;
  int sel_depth_ = 0;
  int previous_score_ = 0;
  std::int64_t start_ms_ = 0;
  std::int64_t hard_deadline_ms_ = 0;
};

/** Milliseconds from a steady clock. */
std::int64_t now_ms();

}  // namespace v3
