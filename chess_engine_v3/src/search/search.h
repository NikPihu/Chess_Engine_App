#pragma once
// The search.
//
// Step 2 (v3.0) ported v2's search: iterative deepening + aspiration windows, negamax alpha-beta
// with PVS, transposition table, reverse futility pruning, null-move pruning, late move reductions,
// killers + history ordering, quiescence search, mate-distance pruning.
//
// Step 3 adds the techniques that modern engines are built on (see docs/phases/v3-03-search.md),
// each behind its own bit in Features so every idea can be tested on its own (SPRT "all" vs
// "all,-name"):
//   hist2      history with "gravity" (saturating updates) + maluses for moves that failed,
//              capture history, and good/bad capture ordering by SEE
//   conthist   continuation history: how good a move is right after the previous move (1 and 2 plies)
//   histlmr    LMR reduced/increased by history, PV-ness and "improving"
//   improving  is the static eval better than two plies ago? (prune less when it is)
//   lmp        late move pruning: skip late quiet moves at low depth
//   fp         futility pruning: skip quiet moves that can't raise alpha
//   seep       SEE pruning of losing captures / quiet moves in the main search
//   qsee       qsearch skips captures that lose material (SEE < 0)
//   qstt       qsearch uses the transposition table
//   tteval     use the TT's stored eval / score to refine the static eval
//   corrhist   correction history: learn, per pawn structure and piece set, how far the static
//              eval tends to be off, and correct it
//   se         singular extensions (+ double extensions, multi-cut, negative extensions)
//   iir        internal iterative reduction: no TT move -> search one ply shallower
//   tm         time management by best-move stability, score trend and node fraction

#include <atomic>
#include <cstdint>
#include <thread>
#include <functional>
#include <memory>
#include <string>
#include <vector>

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
// step 3
constexpr std::uint32_t HIST2 = 1u << 8;
constexpr std::uint32_t CONT_HIST = 1u << 9;
constexpr std::uint32_t HIST_LMR = 1u << 10;
constexpr std::uint32_t IMPROVING = 1u << 11;
constexpr std::uint32_t LMP = 1u << 12;
constexpr std::uint32_t FUTILITY = 1u << 13;
constexpr std::uint32_t SEE_PRUNE = 1u << 14;
constexpr std::uint32_t QS_SEE = 1u << 15;
constexpr std::uint32_t QS_TT = 1u << 16;
constexpr std::uint32_t TT_EVAL = 1u << 17;
constexpr std::uint32_t CORR_HIST = 1u << 18;
constexpr std::uint32_t SINGULAR = 1u << 19;
constexpr std::uint32_t IIR = 1u << 20;
constexpr std::uint32_t SMART_TM = 1u << 21;

/** What v2 plays with (v1's Search2.PROVEN). */
constexpr std::uint32_t PROVEN = KILLERS | HISTORY | PVS | NULL_MOVE | LMR | ASPIRATION | REVERSE_FUTILITY;
/** v3's default: v2's set plus every step-3 technique. */
constexpr std::uint32_t V3 = PROVEN | HIST2 | CONT_HIST | HIST_LMR | IMPROVING | LMP | FUTILITY |
                             SEE_PRUNE | QS_SEE | QS_TT | TT_EVAL | CORR_HIST | SINGULAR | IIR | SMART_TM;
/** Parses e.g. "v3", "proven", "all", "none", "v3,-se", "proven,+lmp" (+ is optional). */
bool parse(const std::string& text, std::uint32_t& out);
std::string describe(std::uint32_t f);
}  // namespace features

struct SearchLimits {
  std::int64_t time[2] = {-1, -1};  // wtime / btime in ms (-1 = no clock)
  std::int64_t inc[2] = {0, 0};
  int moves_to_go = 0;
  std::int64_t move_time = -1;  // ms
  int depth = -1;
  std::uint64_t nodes = 0;  // 0 = no node limit (hard: stops mid-iteration)
  std::uint64_t soft_nodes = 0;  // 0 = none; no new iteration starts after this many nodes
  bool infinite = false;
  bool has_clock() const { return time[WHITE] >= 0 || time[BLACK] >= 0; }
};

/** Base time limits (v1's phase4.TimeManager): soft = remaining/30 + 3/4 increment, hard = 3x soft
 *  (never more than half the clock). The search decides per iteration how much of it to use. */
class TimeManager {
 public:
  TimeManager(const SearchLimits& limits, Color stm);
  std::int64_t soft_ms() const { return soft_; }
  std::int64_t hard_ms() const { return hard_; }
  bool clock() const { return clock_; }

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

/** One search thread. On its own (tests, bench) it owns a TT and a stop flag; inside an Engine
 *  (Lazy SMP) all threads share the Engine's TT, and helper threads stop on the Engine's signal. */
class Search {
 public:
  using InfoFn = std::function<void(const std::string&)>;

  Search();
  /** A worker of a thread pool: shares {@code tt}; helpers ({@code id} > 0) stop on {@code stop}. */
  Search(TranspositionTable* tt, std::atomic<bool>* stop, int id,
         const std::vector<std::unique_ptr<Search>>* pool);

  void set_network(std::shared_ptr<const nnue::Network> net) { net_ = std::move(net); }
  bool has_network() const { return net_ != nullptr; }
  void set_hash_mb(int mb) { tt_->resize(mb); }
  void set_features(std::uint32_t f) { features_ = f; }
  std::uint32_t features() const { return features_; }
  void new_game();

  /** Searches {@code root} within {@code limits}; prints UCI "info" lines through {@code info}. */
  SearchResult think(const Position& root, const SearchLimits& limits, const InfoFn& info);
  /** Can be called from another thread (UCI "stop"). */
  void stop() { stop_->store(true, std::memory_order_relaxed); }
  /** Called before starting a new search (not inside think(), so an early "stop" is never lost). */
  void clear_stop() { stop_->store(false, std::memory_order_relaxed); }
  /** Nodes searched by this thread so far (published every 2048 nodes, safe to read from others). */
  std::uint64_t published_nodes() const { return published_nodes_.load(std::memory_order_relaxed); }

  /** Static evaluation of a position (NNUE, or material if no network): the "eval" command. */
  int evaluate_position(const Position& pos);

 private:
  struct Stack {
    int static_eval = NO_EVAL;
    Move move = MOVE_NONE;      // move played from this ply (MOVE_NONE = null move / root)
    Piece piece = NO_PIECE;     // the piece that made it
    Move excluded = MOVE_NONE;  // singular-extension verification search skips this move
    int double_ext = 0;
  };

  int root_search(int depth);
  int search(int depth, int ply, int alpha, int beta, bool allow_null);
  int quiescence(int ply, int alpha, int beta);
  int raw_evaluate(int ply);
  int correction() const;
  void update_correction(int depth, int diff);
  void make(Move m, int ply);
  void make_null(int ply);
  void score_moves(const MoveList& list, int* scores, Move hash_move, int ply, bool qs);
  int quiet_score(Move m, int ply) const;
  void update_quiet_stats(Move best, int ply, int depth, const Move* tried, int n_tried);
  void update_capture_stats(Move best, int depth, const Move* tried, int n_tried);
  void update_pv(int ply, Move m);
  void check_limits();
  bool on(std::uint32_t f) const { return (features_ & f) != 0; }
  std::string info_line(int depth, int score, std::int64_t elapsed_ms) const;
  std::int16_t* cont_entry(int ply_back, int ply, Piece p, Square to) const;
  std::uint64_t total_nodes() const;

  Position pos_;
  std::shared_ptr<const nnue::Network> net_;
  std::unique_ptr<TranspositionTable> own_tt_;
  TranspositionTable* tt_;
  std::atomic<bool> own_stop_{false};
  std::atomic<bool>* stop_;
  int id_ = 0;  // 0 = main thread (owns time management and output)
  const std::vector<std::unique_ptr<Search>>* pool_ = nullptr;
  std::atomic<std::uint64_t> published_nodes_{0};
  std::uint32_t features_ = features::V3;

  nnue::Accumulator acc_[MAX_PLY + 2];
  MoveList lists_[MAX_PLY + 1];
  int scores_[MAX_PLY + 1][256];
  Move pv_[MAX_PLY + 1][MAX_PLY + 1];
  int pv_length_[MAX_PLY + 1];
  Stack stack_[MAX_PLY + 8];  // stack_[ply + 4]: plies -4..-1 are sentinels
  Stack* ss(int ply) { return &stack_[ply + 4]; }
  const Stack* ss(int ply) const { return &stack_[ply + 4]; }

  // Move-ordering statistics.
  Move killers_[MAX_PLY + 1][2];
  int history_[2][64][64];  // v2 scheme (depth^2, capped) or step-3 gravity scheme
  std::int16_t capture_history_[PIECE_NB][64][PIECE_TYPE_NB];
  // [previous piece][previous to][piece][to]; index 12 = "no previous move" (root / null move).
  std::unique_ptr<std::int16_t[]> cont_history_;
  // Correction history: [side to move][hash % size]
  static constexpr int CORR_SIZE = 16384;
  std::int16_t pawn_corr_[2][CORR_SIZE];
  std::int16_t non_pawn_corr_[2][2][CORR_SIZE];  // [stm][colour whose pieces][index]

  bool aborted_ = false;
  std::uint64_t nodes_ = 0;
  std::uint64_t tb_hits_ = 0;
  std::uint64_t node_limit_ = 0;
  std::uint64_t root_nodes_[64][64];  // nodes spent below each root move (time management)
  int root_depth_ = 0;
  int sel_depth_ = 0;
  int previous_score_ = 0;
  std::int64_t start_ms_ = 0;
  std::int64_t hard_deadline_ms_ = 0;
};

/**
 * Lazy SMP: N threads search the same position at the same time and share one transposition
 * table. They don't divide the work explicitly; each one naturally explores a slightly different
 * part of the tree (timing, history tables), and every result one thread stores in the TT helps
 * the others. Thread 0 is the "main" thread: it owns the clock, prints info and picks the move;
 * when it finishes, the helpers are stopped.
 */
class Engine {
 public:
  Engine();
  ~Engine();

  void set_network(std::shared_ptr<const nnue::Network> net);
  bool has_network() const { return workers_[0]->has_network(); }
  void set_hash_mb(int mb) { tt_.resize(mb); }
  void set_features(std::uint32_t f);
  std::uint32_t features() const { return workers_[0]->features(); }
  void set_threads(int n);
  int threads() const { return int(workers_.size()); }
  void new_game();

  SearchResult think(const Position& root, const SearchLimits& limits, const Search::InfoFn& info);
  void stop() { stop_.store(true, std::memory_order_relaxed); }
  void clear_stop() { stop_.store(false, std::memory_order_relaxed); }
  int evaluate_position(const Position& pos) { return workers_[0]->evaluate_position(pos); }

 private:
  TranspositionTable tt_{16};
  std::atomic<bool> stop_{false};          // UCI "stop": seen by the main thread
  std::atomic<bool> helpers_stop_{false};  // set when the main thread is done
  std::vector<std::unique_ptr<Search>> workers_;
  std::shared_ptr<const nnue::Network> net_;
};

/** Milliseconds from a steady clock. */
std::int64_t now_ms();

}  // namespace v3
