#include "search/search.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>
#include <sstream>

namespace v3 {

std::int64_t now_ms() {
  using namespace std::chrono;
  return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

// ---------------------------------------------------------------- features

namespace {
struct FeatureName {
  const char* name;
  std::uint32_t bit;
};
const FeatureName FEATURE_NAMES[] = {
    {"killers", features::KILLERS},   {"history", features::HISTORY},
    {"pvs", features::PVS},           {"nmp", features::NULL_MOVE},
    {"lmr", features::LMR},           {"check", features::CHECK_EXTENSION},
    {"aspiration", features::ASPIRATION}, {"rfp", features::REVERSE_FUTILITY},
    {"hist2", features::HIST2},       {"conthist", features::CONT_HIST},
    {"histlmr", features::HIST_LMR},  {"improving", features::IMPROVING},
    {"lmp", features::LMP},           {"fp", features::FUTILITY},
    {"seep", features::SEE_PRUNE},    {"qsee", features::QS_SEE},
    {"qstt", features::QS_TT},        {"tteval", features::TT_EVAL},
    {"corrhist", features::CORR_HIST}, {"se", features::SINGULAR},
    {"iir", features::IIR},           {"tm", features::SMART_TM},
};
}  // namespace

bool features::parse(const std::string& text, std::uint32_t& out) {
  std::uint32_t all = 0;
  for (const FeatureName& n : FEATURE_NAMES) all |= n.bit;
  std::uint32_t result = 0;
  std::stringstream ss(text);
  std::string item;
  while (std::getline(ss, item, ',')) {
    item.erase(std::remove_if(item.begin(), item.end(), [](unsigned char c) { return std::isspace(c); }),
               item.end());
    if (item.empty()) continue;
    bool remove = item[0] == '-';
    if (item[0] == '-' || item[0] == '+') item.erase(0, 1);
    std::uint32_t bit = 0;
    if (item == "all") bit = all;
    else if (item == "none") bit = 0;
    else if (item == "proven") bit = PROVEN;
    else if (item == "v3" || item == "default") bit = V3;
    else {
      bool found = false;
      for (const FeatureName& n : FEATURE_NAMES)
        if (item == n.name) bit = n.bit, found = true;
      if (!found) return false;
    }
    result = remove ? result & ~bit : result | bit;
  }
  out = result;
  return true;
}

std::string features::describe(std::uint32_t f) {
  std::string s;
  for (const FeatureName& n : FEATURE_NAMES)
    if (f & n.bit) s += (s.empty() ? "" : ",") + std::string(n.name);
  return s.empty() ? "none" : s;
}

// ---------------------------------------------------------------- time

TimeManager::TimeManager(const SearchLimits& limits, Color stm) {
  constexpr std::int64_t SAFETY_MS = 15;
  if (limits.move_time > 0) {
    soft_ = hard_ = std::max<std::int64_t>(1, limits.move_time - SAFETY_MS);
  } else if (limits.has_clock() && !limits.infinite && limits.time[stm] >= 0) {
    std::int64_t remaining = limits.time[stm];
    std::int64_t increment = limits.inc[stm];
    int moves_left = limits.moves_to_go > 0 ? limits.moves_to_go : 30;
    std::int64_t usable = std::max<std::int64_t>(1, remaining - 3 * SAFETY_MS);
    std::int64_t soft = remaining / moves_left + increment * 3 / 4;
    soft_ = std::max<std::int64_t>(1, std::min(soft, usable));
    hard_ = std::max<std::int64_t>(1, std::min(soft_ * 3, std::max<std::int64_t>(1, remaining / 2)));
    clock_ = true;
  }
}

// ---------------------------------------------------------------- tables and helpers

namespace {

int LMR_TABLE[64][64];

struct LmrInit {
  LmrInit() {
    for (int d = 1; d < 64; d++)
      for (int m = 1; m < 64; m++)
        LMR_TABLE[d][m] = int(0.75 + std::log(double(d)) * std::log(double(m)) / 2.25);
  }
} lmr_init;

// v2 move-ordering scores (kept for the "proven" configuration).
constexpr int HASH_MOVE_SCORE = 10'000'000;
constexpr int CAPTURE_SCORE = 1'000'000;
constexpr int PROMOTION_SCORE = 900'000;
constexpr int KILLER_1_SCORE = 800'000;
constexpr int KILLER_2_SCORE = 790'000;
constexpr int HISTORY_MAX = 500'000;
// step-3 move-ordering bands: good captures > queen promotions > killers > quiets > bad captures.
constexpr int GOOD_CAPTURE = 4'000'000;
constexpr int QUEEN_PROMO = 3'900'000;
constexpr int KILLER_1 = 3'000'000;
constexpr int KILLER_2 = 2'900'000;
constexpr int BAD_CAPTURE = -3'000'000;
constexpr int UNDER_PROMO = -4'000'000;
// Gravity limit for the step-3 histories: an entry can never leave [-HIST_LIMIT, HIST_LIMIT].
constexpr int HIST_LIMIT = 16384;
constexpr int CORR_LIMIT = 1024;

// Indexed by PieceType: pawn, knight, bishop, rook, queen, king.
constexpr int VALUE[6] = {100, 320, 330, 500, 900, 2000};

bool is_quiet(const Position& pos, Move m) {
  return pos.piece_on(move_to(m)) == NO_PIECE && move_flag(m) != FLAG_EN_PASSANT && !is_promotion(m);
}

PieceType captured_type(const Position& pos, Move m) {
  return move_flag(m) == FLAG_EN_PASSANT ? PAWN : type_of(pos.piece_on(move_to(m)));
}

void pick_next(MoveList& list, int* scores, int index) {
  int best = index;
  for (int i = index + 1; i < list.size; i++)
    if (scores[i] > scores[best]) best = i;
  if (best != index) {
    std::swap(list.moves[index], list.moves[best]);
    std::swap(scores[index], scores[best]);
  }
}

int material_eval(const Position& pos) {
  int score = 0;
  for (PieceType t : {PAWN, KNIGHT, BISHOP, ROOK, QUEEN})
    score += VALUE[t] * (popcount(pos.pieces(WHITE, t)) - popcount(pos.pieces(BLACK, t)));
  return pos.side_to_move() == WHITE ? score : -score;
}

bool has_non_pawn_material(const Position& pos) {
  Color us = pos.side_to_move();
  return (pos.pieces(us) & ~(pos.pieces(us, PAWN) | pos.pieces(us, KING))) != 0;
}

/** "History gravity": move the entry towards +-limit by bonus, slower the closer it already is.
 *  Old knowledge fades automatically and no entry can overflow. */
template <typename T>
void gravity(T& entry, int bonus, int limit) {
  bonus = std::clamp(bonus, -limit, limit);
  entry = T(entry + bonus - entry * std::abs(bonus) / limit);
}

int history_bonus(int depth) { return std::min(1600, 170 * depth - 70); }

}  // namespace

// ---------------------------------------------------------------- setup

Search::Search()
    : own_tt_(std::make_unique<TranspositionTable>(16)),
      tt_(own_tt_.get()),
      stop_(&own_stop_),
      cont_history_(std::make_unique<std::int16_t[]>(13 * 64 * PIECE_NB * 64)) {
  new_game();
}

Search::Search(TranspositionTable* tt, std::atomic<bool>* stop, int id,
               const std::vector<std::unique_ptr<Search>>* pool)
    : tt_(tt),
      stop_(stop),
      id_(id),
      pool_(pool),
      cont_history_(std::make_unique<std::int16_t[]>(13 * 64 * PIECE_NB * 64)) {
  new_game();
}

std::uint64_t Search::total_nodes() const {
  if (!pool_) return nodes_;
  std::uint64_t total = 0;
  for (const auto& w : *pool_) total += w.get() == this ? nodes_ : w->published_nodes();
  return total;
}

void Search::new_game() {
  if (own_tt_) tt_->clear();
  std::memset(killers_, 0, sizeof(killers_));
  std::memset(history_, 0, sizeof(history_));
  std::memset(capture_history_, 0, sizeof(capture_history_));
  std::memset(cont_history_.get(), 0, sizeof(std::int16_t) * 13 * 64 * PIECE_NB * 64);
  std::memset(pawn_corr_, 0, sizeof(pawn_corr_));
  std::memset(non_pawn_corr_, 0, sizeof(non_pawn_corr_));
}

std::int16_t* Search::cont_entry(int ply_back, int ply, Piece p, Square to) const {
  const Stack* prev = ss(ply - ply_back);
  int prev_piece = prev->move == MOVE_NONE ? 12 : prev->piece;
  int prev_to = prev->move == MOVE_NONE ? 0 : move_to(prev->move);
  return &cont_history_[((prev_piece * 64 + prev_to) * PIECE_NB + p) * 64 + to];
}

int Search::evaluate_position(const Position& pos) {
  if (!net_) return material_eval(pos);
  nnue::Accumulator a;
  a.refresh(*net_, pos);
  return a.evaluate(*net_, pos);
}

int Search::raw_evaluate(int ply) {
  if (!net_) return material_eval(pos_);
  return acc_[ply].evaluate(*net_, pos_);
}

int Search::correction() const {
  if (!on(features::CORR_HIST)) return 0;
  const Color c = pos_.side_to_move();
  int pawn = pawn_corr_[c][pos_.pawn_key() % CORR_SIZE];
  int white = non_pawn_corr_[c][WHITE][pos_.non_pawn_key(WHITE) % CORR_SIZE];
  int black = non_pawn_corr_[c][BLACK][pos_.non_pawn_key(BLACK) % CORR_SIZE];
  return (2 * pawn + white + black) / 8;
}

void Search::update_correction(int depth, int diff) {
  const Color c = pos_.side_to_move();
  int bonus = std::clamp(diff * depth / 8, -256, 256);
  gravity(pawn_corr_[c][pos_.pawn_key() % CORR_SIZE], bonus, CORR_LIMIT);
  gravity(non_pawn_corr_[c][WHITE][pos_.non_pawn_key(WHITE) % CORR_SIZE], bonus, CORR_LIMIT);
  gravity(non_pawn_corr_[c][BLACK][pos_.non_pawn_key(BLACK) % CORR_SIZE], bonus, CORR_LIMIT);
}

void Search::make(Move m, int ply) {
  Stack* s = ss(ply);
  s->move = m;
  s->piece = pos_.piece_on(move_from(m));
  const nnue::Delta delta = net_ ? nnue::delta_for(pos_, m) : nnue::Delta{};
  pos_.make_move(m);
  if (net_) acc_[ply + 1].update(*net_, pos_, delta, acc_[ply]);
  tt_->prefetch(pos_.key());
}

void Search::make_null(int ply) {
  Stack* s = ss(ply);
  s->move = MOVE_NONE;
  s->piece = NO_PIECE;
  if (net_) acc_[ply + 1] = acc_[ply];
  pos_.make_null();
}

void Search::check_limits() {
  published_nodes_.store(nodes_, std::memory_order_relaxed);
  if (stop_->load(std::memory_order_relaxed) || (node_limit_ && nodes_ >= node_limit_) ||
      (hard_deadline_ms_ > 0 && now_ms() >= hard_deadline_ms_))
    aborted_ = true;
}

void Search::update_pv(int ply, Move m) {
  pv_[ply][ply] = m;
  int child = pv_length_[ply + 1];
  for (int i = ply + 1; i < child; i++) pv_[ply][i] = pv_[ply + 1][i];
  pv_length_[ply] = std::max(child, ply + 1);
}

// ---------------------------------------------------------------- move ordering

int Search::quiet_score(Move m, int ply) const {
  const Color side = pos_.side_to_move();
  int score = history_[side][move_from(m)][move_to(m)];
  if (on(features::CONT_HIST)) {
    Piece p = pos_.piece_on(move_from(m));
    Square to = move_to(m);
    score += *cont_entry(1, ply, p, to) + *cont_entry(2, ply, p, to);
  }
  return score;
}

void Search::score_moves(const MoveList& list, int* scores, Move hash_move, int ply, bool qs) {
  const Color side = pos_.side_to_move();
  const bool modern = on(features::HIST2);
  for (int i = 0; i < list.size; i++) {
    Move m = list.moves[i];
    if (m == hash_move) {
      scores[i] = HASH_MOVE_SCORE * 2;
      continue;
    }
    const int flag = move_flag(m);
    const Piece victim = pos_.piece_on(move_to(m));
    const bool capture = victim != NO_PIECE || flag == FLAG_EN_PASSANT;

    if (modern) {
      // Step 3: captures by victim value + capture history, split by SEE into good (before
      // killers/quiets) and bad (after all quiets); quiets by history + continuation history.
      if (is_promotion(m) && promotion_type(m) != QUEEN) {
        scores[i] = UNDER_PROMO;
      } else if (capture) {
        PieceType vt = captured_type(pos_, m);
        int s = VALUE[vt] * 16 + capture_history_[pos_.piece_on(move_from(m))][move_to(m)][vt];
        if (is_promotion(m)) s += VALUE[QUEEN];
        scores[i] = (qs || pos_.see_ge(m, -s / 64) ? GOOD_CAPTURE : BAD_CAPTURE) + s;
      } else if (is_promotion(m)) {
        scores[i] = QUEEN_PROMO;
      } else if (!qs && on(features::KILLERS) && m == killers_[ply][0]) {
        scores[i] = KILLER_1;
      } else if (!qs && on(features::KILLERS) && m == killers_[ply][1]) {
        scores[i] = KILLER_2;
      } else {
        scores[i] = qs ? 0 : quiet_score(m, ply);
      }
      continue;
    }

    // v2's ordering (phase4.MoveOrdering for qsearch, phase6.MoveOrdering2 for the main search).
    int score = 0;
    if (capture) {
      int victim_type = flag == FLAG_EN_PASSANT ? PAWN : type_of(victim);
      int attacker_type = type_of(pos_.piece_on(move_from(m)));
      score = CAPTURE_SCORE + VALUE[victim_type] * 10 - VALUE[attacker_type] / 10;
    }
    if (qs) {
      if (is_promotion(m)) score += PROMOTION_SCORE + VALUE[promotion_type(m)];
    } else if (!capture) {
      if (is_promotion(m)) score = PROMOTION_SCORE + VALUE[promotion_type(m)];
      else if (on(features::KILLERS) && m == killers_[ply][0]) score = KILLER_1_SCORE;
      else if (on(features::KILLERS) && m == killers_[ply][1]) score = KILLER_2_SCORE;
      else if (on(features::HISTORY)) score = history_[side][move_from(m)][move_to(m)];
    } else if (is_promotion(m)) {
      score += VALUE[promotion_type(m)];
    }
    scores[i] = score;
  }
}

void Search::update_quiet_stats(Move best, int ply, int depth, const Move* tried, int n_tried) {
  const Color side = pos_.side_to_move();
  if (on(features::KILLERS) && killers_[ply][0] != best) {
    killers_[ply][1] = killers_[ply][0];
    killers_[ply][0] = best;
  }
  if (!on(features::HISTORY)) return;
  if (!on(features::HIST2)) {
    int& h = history_[side][move_from(best)][move_to(best)];
    h = std::min(HISTORY_MAX, h + depth * depth);
    return;
  }
  const int bonus = history_bonus(depth);
  auto apply = [&](Move m, int b) {
    gravity(history_[side][move_from(m)][move_to(m)], b, HIST_LIMIT);
    if (on(features::CONT_HIST)) {
      Piece p = pos_.piece_on(move_from(m));
      Square to = move_to(m);
      if (ss(ply - 1)->move != MOVE_NONE) gravity(*cont_entry(1, ply, p, to), b, HIST_LIMIT);
      if (ss(ply - 2)->move != MOVE_NONE) gravity(*cont_entry(2, ply, p, to), b, HIST_LIMIT);
    }
  };
  apply(best, bonus);
  for (int i = 0; i < n_tried; i++)
    if (tried[i] != best) apply(tried[i], -bonus);  // malus: these quiets were tried and failed
}

void Search::update_capture_stats(Move best, int depth, const Move* tried, int n_tried) {
  if (!on(features::HIST2)) return;
  const int bonus = history_bonus(depth);
  auto apply = [&](Move m, int b) {
    Piece p = pos_.piece_on(move_from(m));
    gravity(capture_history_[p][move_to(m)][captured_type(pos_, m)], b, HIST_LIMIT);
  };
  if (!is_quiet(pos_, best)) apply(best, bonus);
  for (int i = 0; i < n_tried; i++)
    if (tried[i] != best) apply(tried[i], -bonus);
}

// ---------------------------------------------------------------- iterative deepening

SearchResult Search::think(const Position& root, const SearchLimits& limits, const InfoFn& info) {
  pos_ = root;
  nodes_ = 0;
  aborted_ = false;
  sel_depth_ = 0;
  previous_score_ = 0;
  node_limit_ = id_ == 0 ? limits.nodes : 0;
  published_nodes_.store(0, std::memory_order_relaxed);
  if (id_ == 0) tt_->new_search();  // helpers share the main thread's TT generation
  std::memset(killers_, 0, sizeof(killers_));
  std::memset(root_nodes_, 0, sizeof(root_nodes_));
  for (Stack& s : stack_) s = Stack{};
  if (!on(features::HIST2))
    for (auto& side : history_)
      for (auto& from : side)
        for (int& h : from) h /= 2;
  if (net_) acc_[0].refresh(*net_, pos_);

  SearchResult result;
  MoveList root_moves;
  generate(pos_, root_moves);
  if (root_moves.size == 0) return result;

  TimeManager time(limits, pos_.side_to_move());
  start_ms_ = now_ms();
  // Helpers have no clock: they search until the main thread tells them to stop.
  hard_deadline_ms_ = id_ == 0 && time.hard_ms() > 0 ? start_ms_ + time.hard_ms() : 0;
  int max_depth = limits.depth > 0 ? std::min(limits.depth, MAX_PLY - 1) : MAX_PLY - 1;

  result.best = root_moves.moves[0];
  Move last_best = MOVE_NONE;
  int stability = 0;
  int last_score = 0;
  for (int depth = 1; depth <= max_depth; depth++) {
    root_depth_ = depth;
    int score = root_search(depth);
    if (aborted_) {
      // A move that beat the previous best during the unfinished iteration is still safe to use.
      if (pv_length_[0] > 0 && pv_[0][0] != MOVE_NONE) result.best = pv_[0][0];
      break;
    }
    result.best = pv_[0][0];
    result.score = score;
    result.depth = depth;
    std::int64_t elapsed = now_ms() - start_ms_;
    if (info && id_ == 0) info(info_line(depth, score, elapsed));
    if (id_ != 0) continue;  // only the main thread decides when to stop
    if (limits.soft_nodes && nodes_ >= limits.soft_nodes) break;

    bool mate_found = std::abs(score) >= MATE_THRESHOLD;
    if (mate_found && limits.depth < 0 && depth >= MATE - std::abs(score)) break;

    if (!time.clock()) {
      last_best = result.best;
      last_score = score;
      continue;
    }
    if (on(features::SMART_TM)) {
      // Spend more time when the best move keeps changing, the score is dropping, or the best
      // move took only a small share of the effort (the alternatives were hard to refute).
      stability = result.best == last_best ? std::min(stability + 1, 4) : 0;
      static constexpr double STABILITY[5] = {2.0, 1.4, 1.1, 0.9, 0.75};
      double score_factor = depth > 1 ? std::clamp(1.0 + (last_score - score) / 100.0, 0.8, 1.6) : 1.0;
      Move b = result.best;
      double fraction = nodes_ ? double(root_nodes_[move_from(b)][move_to(b)]) / double(nodes_) : 0.5;
      double node_factor = std::clamp((1.6 - fraction) * 1.25, 0.6, 1.8);
      double optimum = 0.5 * double(time.soft_ms());
      if (depth >= 4 && elapsed >= optimum * STABILITY[stability] * score_factor * node_factor) break;
    } else if (elapsed >= time.soft_ms() / 2) {
      break;  // v2: the next iteration would probably not finish anyway
    }
    last_best = result.best;
    last_score = score;
  }
  published_nodes_.store(nodes_, std::memory_order_relaxed);
  result.nodes = total_nodes();
  return result;
}

int Search::root_search(int depth) {
  if (!on(features::ASPIRATION) || depth < 5) {
    previous_score_ = search(depth, 0, -INFINITE_SCORE, INFINITE_SCORE, false);
    return previous_score_;
  }
  int delta = 25;
  int alpha = std::max(-INFINITE_SCORE, previous_score_ - delta);
  int beta = std::min(INFINITE_SCORE, previous_score_ + delta);
  while (true) {
    int score = search(depth, 0, alpha, beta, false);
    if (aborted_) return score;
    if (score <= alpha) alpha = std::max(-INFINITE_SCORE, alpha - delta);
    else if (score >= beta) beta = std::min(INFINITE_SCORE, beta + delta);
    else {
      previous_score_ = score;
      return score;
    }
    delta *= 2;
    if (delta > 1000) {
      alpha = -INFINITE_SCORE;
      beta = INFINITE_SCORE;
    }
  }
}

// ---------------------------------------------------------------- main search

int Search::search(int depth, int ply, int alpha, int beta, bool allow_null) {
  pv_length_[ply] = ply;
  if ((nodes_ & 2047) == 0) check_limits();
  if (aborted_) return 0;
  const bool pv_node = beta - alpha > 1;
  const bool root = ply == 0;
  Stack* const s = ss(ply);
  const Move excluded = s->excluded;

  if (!root) {
    if (pos_.is_draw(ply)) return 0;
    alpha = std::max(alpha, -MATE + ply);
    beta = std::min(beta, MATE - ply - 1);
    if (alpha >= beta) return alpha;
  }

  const bool in_check = pos_.in_check();
  if (in_check && on(features::CHECK_EXTENSION)) depth++;
  if (depth <= 0) return quiescence(ply, alpha, beta);
  if (ply >= MAX_PLY - 1) return raw_evaluate(ply);
  nodes_++;
  (s + 1)->excluded = MOVE_NONE;
  (s + 1)->double_ext = s->double_ext;

  // --- transposition table
  const Key key = pos_.key();
  TTHit tt;
  const bool tt_hit = tt_->probe(key, ply, tt);
  const Move tt_move = tt_hit ? tt.move : MOVE_NONE;
  if (tt_hit && !pv_node && !excluded && tt.depth >= depth &&
      (tt.bound == BOUND_EXACT || (tt.bound == BOUND_LOWER && tt.score >= beta) ||
       (tt.bound == BOUND_UPPER && tt.score <= alpha)))
    return tt.score;

  // --- static evaluation (corrected by correction history, refined by the TT score)
  int raw_eval = NO_EVAL, eval = -INFINITE_SCORE;
  if (in_check) {
    s->static_eval = NO_EVAL;
  } else if (excluded) {
    eval = s->static_eval;  // same position as the parent search: already computed
  } else {
    raw_eval = (tt_hit && tt.eval != NO_EVAL && on(features::TT_EVAL)) ? tt.eval : raw_evaluate(ply);
    s->static_eval = std::clamp(raw_eval + correction(), -MATE_THRESHOLD + 1, MATE_THRESHOLD - 1);
    eval = s->static_eval;
    if (on(features::TT_EVAL) && tt_hit && std::abs(tt.score) < MATE_THRESHOLD &&
        (tt.bound == BOUND_EXACT || (tt.bound == BOUND_LOWER && tt.score > eval) ||
         (tt.bound == BOUND_UPPER && tt.score < eval)))
      eval = tt.score;
  }
  const int static_eval = in_check ? -INFINITE_SCORE : eval;
  const bool improving = on(features::IMPROVING) && !in_check && ply >= 2 &&
                         ss(ply - 2)->static_eval != NO_EVAL && s->static_eval > ss(ply - 2)->static_eval;

  // --- reverse futility pruning: far above beta at low depth -> assume it holds
  if (on(features::REVERSE_FUTILITY) && !pv_node && !in_check && !excluded && depth <= 6 &&
      std::abs(beta) < MATE_THRESHOLD && static_eval - 80 * (depth - (improving ? 1 : 0)) >= beta)
    return static_eval;

  // --- null move pruning: even passing keeps us above beta -> cut
  if (on(features::NULL_MOVE) && allow_null && !pv_node && !in_check && !excluded && depth >= 3 &&
      static_eval >= beta && has_non_pawn_material(pos_)) {
    int reduction = 3 + depth / 6;
    make_null(ply);
    int score = -search(depth - 1 - reduction, ply + 1, -beta, -beta + 1, false);
    pos_.unmake_null();
    if (aborted_) return 0;
    if (score >= beta) return score >= MATE_THRESHOLD ? beta : score;
  }

  // --- internal iterative reduction: no TT move here means this node was never searched well;
  //     search it a ply shallower (the next iteration will find a TT move)
  if (on(features::IIR) && !excluded && depth >= 4 && tt_move == MOVE_NONE) depth--;

  MoveList& moves = lists_[ply];
  moves.size = 0;
  generate(pos_, moves);
  if (moves.size == 0) return excluded ? alpha : (in_check ? -MATE + ply : 0);

  // --- singular extension: is the TT move much better than every alternative? Then search it
  //     deeper. Verified by a reduced search of all *other* moves against a lowered bound.
  int singular_ext = 0;
  if (on(features::SINGULAR) && !root && !excluded && tt_move != MOVE_NONE && depth >= 7 &&
      ply < 2 * root_depth_ && tt.depth >= depth - 3 && tt.bound != BOUND_UPPER &&
      std::abs(tt.score) < MATE_THRESHOLD && moves.contains(tt_move)) {
    const int s_beta = tt.score - 2 * depth;
    s->excluded = tt_move;
    int value = search((depth - 1) / 2, ply, s_beta - 1, s_beta, false);
    s->excluded = MOVE_NONE;
    if (aborted_) return 0;
    if (value < s_beta) {
      singular_ext = 1;
      if (!pv_node && value < s_beta - 20 && s->double_ext < 6) {
        singular_ext = 2;
        (s + 1)->double_ext = s->double_ext + 1;
      }
    } else if (s_beta >= beta) {
      return s_beta;  // multi-cut: even without the TT move another move beats beta
    } else if (tt.score >= beta) {
      singular_ext = -1;  // the TT move is not special: search it a bit shallower
    }
    // The verification search reused this ply's move list: regenerate it.
    moves.size = 0;
    generate(pos_, moves);
  }

  int* scores = scores_[ply];
  score_moves(moves, scores, tt_move, ply, false);

  const int original_alpha = alpha;
  int best_score = -INFINITE_SCORE;
  Move best_move = MOVE_NONE;
  int move_count = 0;
  bool skip_quiets = false;
  Move quiets_tried[64], captures_tried[32];
  int n_quiets = 0, n_captures = 0;

  for (int i = 0; i < moves.size; i++) {
    pick_next(moves, scores, i);
    const Move m = moves.moves[i];
    if (m == excluded) continue;
    const bool quiet = is_quiet(pos_, m);
    if (quiet && skip_quiets) continue;
    move_count++;

    // --- shallow-depth pruning (never when every move so far loses to a mate)
    if (!root && best_score > -MATE_THRESHOLD && has_non_pawn_material(pos_)) {
      const int lmr_depth =
          std::max(0, depth - 1 - LMR_TABLE[std::min(depth, 63)][std::min(move_count, 63)]);
      if (quiet) {
        if (on(features::LMP) && !pv_node && !in_check && depth <= 8 &&
            move_count > (3 + depth * depth) / (improving ? 1 : 2)) {
          skip_quiets = true;
          continue;
        }
        if (on(features::FUTILITY) && !in_check && lmr_depth <= 8 &&
            static_eval + 100 + 100 * lmr_depth <= alpha) {
          skip_quiets = true;
          continue;
        }
        if (on(features::SEE_PRUNE) && lmr_depth <= 8 && !pos_.see_ge(m, -30 * lmr_depth * lmr_depth))
          continue;
      } else if (on(features::SEE_PRUNE) && depth <= 8 && !pos_.see_ge(m, -90 * depth)) {
        continue;
      }
    }

    const std::uint64_t nodes_before = nodes_;
    const int hist = quiet && on(features::HIST_LMR) ? quiet_score(m, ply) : 0;
    make(m, ply);
    const bool gives_check = pos_.in_check();
    const int new_depth = depth - 1 + (m == tt_move ? singular_ext : 0);
    int score;

    if (move_count == 1) {
      score = -search(new_depth, ply + 1, -beta, -alpha, true);
    } else {
      int reduction = 0;
      if (on(features::LMR) && depth >= 3 && move_count >= 3 && quiet && !in_check && !gives_check) {
        reduction = LMR_TABLE[std::min(depth, 63)][std::min(move_count, 63)];
        if (on(features::HIST_LMR)) {
          // Reduce more in non-PV nodes and when not improving; less for moves with good history.
          reduction += !pv_node;
          reduction += !improving;
          reduction -= std::clamp(hist / 8192, -2, 2);
        }
        reduction = std::max(0, std::min(reduction, new_depth - 1));
      }
      bool full = true;
      score = alpha;
      if (reduction > 0) {
        score = -search(new_depth - reduction, ply + 1, -alpha - 1, -alpha, true);
        full = score > alpha;
      }
      if (full) {
        if (on(features::PVS)) {
          score = -search(new_depth, ply + 1, -alpha - 1, -alpha, true);
          if (score > alpha && score < beta) score = -search(new_depth, ply + 1, -beta, -alpha, true);
        } else {
          score = -search(new_depth, ply + 1, -beta, -alpha, true);
        }
      }
    }
    pos_.unmake_move(m);
    if (aborted_) return 0;
    if (root) root_nodes_[move_from(m)][move_to(m)] += nodes_ - nodes_before;

    if (score > best_score) {
      best_score = score;
      best_move = m;
      if (score > alpha) {
        alpha = score;
        update_pv(ply, m);
        if (alpha >= beta) {
          if (quiet) update_quiet_stats(m, ply, depth, quiets_tried, n_quiets);
          update_capture_stats(m, depth, captures_tried, n_captures);
          break;
        }
      }
    }
    if (quiet && n_quiets < 64) quiets_tried[n_quiets++] = m;
    else if (!quiet && n_captures < 32) captures_tried[n_captures++] = m;
  }

  if (move_count == 0) return excluded ? alpha : (in_check ? -MATE + ply : 0);

  const Bound bound = best_score >= beta ? BOUND_LOWER
                      : best_score > original_alpha ? BOUND_EXACT
                                                    : BOUND_UPPER;
  if (!excluded) {
    tt_->store(key, best_move, best_score, raw_eval, depth, bound, ply);
    // Correction history learns how far the static eval was from what the search found -
    // only from trustworthy, quiet results (a capture's score says little about the eval).
    if (on(features::CORR_HIST) && !in_check && (best_move == MOVE_NONE || is_quiet(pos_, best_move)) &&
        !(bound == BOUND_LOWER && best_score <= s->static_eval) &&
        !(bound == BOUND_UPPER && best_score >= s->static_eval) && std::abs(best_score) < MATE_THRESHOLD)
      update_correction(depth, best_score - s->static_eval);
  }
  return best_score;
}

// ---------------------------------------------------------------- quiescence

int Search::quiescence(int ply, int alpha, int beta) {
  pv_length_[ply] = ply;
  if ((nodes_ & 2047) == 0) check_limits();
  if (aborted_) return 0;
  nodes_++;
  sel_depth_ = std::max(sel_depth_, ply);
  if (ply >= MAX_PLY - 1) return raw_evaluate(ply);
  const bool pv_node = beta - alpha > 1;

  const Key key = pos_.key();
  TTHit tt;
  const bool tt_hit = on(features::QS_TT) && tt_->probe(key, ply, tt);
  if (tt_hit && !pv_node &&
      (tt.bound == BOUND_EXACT || (tt.bound == BOUND_LOWER && tt.score >= beta) ||
       (tt.bound == BOUND_UPPER && tt.score <= alpha)))
    return tt.score;

  const bool in_check = pos_.in_check();
  MoveList& moves = lists_[ply];
  moves.size = 0;
  int best_score;
  int raw_eval = NO_EVAL;
  if (in_check) {
    // In check there is no "standing pat": every evasion must be considered.
    generate(pos_, moves, ALL);
    if (moves.size == 0) return -MATE + ply;
    best_score = -INFINITE_SCORE;
  } else {
    raw_eval = (tt_hit && tt.eval != NO_EVAL && on(features::TT_EVAL)) ? tt.eval : raw_evaluate(ply);
    int stand_pat = std::clamp(raw_eval + correction(), -MATE_THRESHOLD + 1, MATE_THRESHOLD - 1);
    if (on(features::TT_EVAL) && tt_hit && std::abs(tt.score) < MATE_THRESHOLD &&
        (tt.bound == BOUND_EXACT || (tt.bound == BOUND_LOWER && tt.score > stand_pat) ||
         (tt.bound == BOUND_UPPER && tt.score < stand_pat)))
      stand_pat = tt.score;
    if (stand_pat >= beta) return stand_pat;
    alpha = std::max(alpha, stand_pat);
    best_score = stand_pat;
    generate(pos_, moves, NOISY);
    // Captures and queen promotions only (under-promotions wait for the main search).
    int n = 0;
    for (int i = 0; i < moves.size; i++) {
      Move m = moves.moves[i];
      if (!is_promotion(m) || promotion_type(m) == QUEEN) moves.moves[n++] = m;
    }
    moves.size = n;
  }
  int* scores = scores_[ply];
  score_moves(moves, scores, tt_hit ? tt.move : MOVE_NONE, ply, true);
  Move best_move = MOVE_NONE;
  for (int i = 0; i < moves.size; i++) {
    pick_next(moves, scores, i);
    const Move m = moves.moves[i];
    // A capture that loses material (SEE < 0) cannot improve a quiet position's score.
    if (!in_check && on(features::QS_SEE) && !pos_.see_ge(m, 0)) continue;
    make(m, ply);
    int score = -quiescence(ply + 1, -beta, -alpha);
    pos_.unmake_move(m);
    if (aborted_) return 0;
    if (score > best_score) {
      best_score = score;
      if (score > alpha) {
        alpha = score;
        best_move = m;
        update_pv(ply, m);
        if (alpha >= beta) break;
      }
    }
  }
  if (on(features::QS_TT))
    tt_->store(key, best_move, best_score, raw_eval, 0, best_score >= beta ? BOUND_LOWER : BOUND_UPPER, ply);
  return best_score;
}

std::string Search::info_line(int depth, int score, std::int64_t elapsed_ms) const {
  std::ostringstream o;
  o << "info depth " << depth << " seldepth " << std::max(sel_depth_, depth);
  if (std::abs(score) >= MATE_THRESHOLD) {
    int plies = MATE - std::abs(score);
    int moves = (plies + 1) / 2;
    o << " score mate " << (score > 0 ? moves : -moves);
  } else {
    o << " score cp " << score;
  }
  const std::uint64_t nodes = total_nodes();
  o << " nodes " << nodes << " nps " << (elapsed_ms > 0 ? nodes * 1000 / elapsed_ms : nodes)
    << " hashfull " << tt_->hashfull() << " time " << elapsed_ms << " pv";
  for (int i = 0; i < pv_length_[0]; i++) o << ' ' << move_to_uci(pv_[0][i]);
  return o.str();
}

// ---------------------------------------------------------------- Lazy SMP engine

Engine::Engine() { set_threads(1); }

Engine::~Engine() = default;

void Engine::set_threads(int n) {
  n = std::clamp(n, 1, 256);
  std::uint32_t f = workers_.empty() ? features::V3 : workers_[0]->features();
  workers_.clear();
  for (int i = 0; i < n; i++) {
    std::atomic<bool>* stop = i == 0 ? &stop_ : &helpers_stop_;
    workers_.push_back(std::make_unique<Search>(&tt_, stop, i, &workers_));
    workers_.back()->set_network(net_);
    workers_.back()->set_features(f);
  }
}

void Engine::set_network(std::shared_ptr<const nnue::Network> net) {
  net_ = std::move(net);
  for (auto& w : workers_) w->set_network(net_);
}

void Engine::set_features(std::uint32_t f) {
  for (auto& w : workers_) w->set_features(f);
}

void Engine::new_game() {
  tt_.clear();
  for (auto& w : workers_) w->new_game();
}

SearchResult Engine::think(const Position& root, const SearchLimits& limits, const Search::InfoFn& info) {
  helpers_stop_.store(false, std::memory_order_relaxed);
  std::vector<std::thread> helpers;
  SearchLimits helper_limits;  // no clock, no depth or node limit: they run until told to stop
  helper_limits.infinite = true;
  for (std::size_t i = 1; i < workers_.size(); i++)
    helpers.emplace_back([this, i, &root, &helper_limits]() { workers_[i]->think(root, helper_limits, nullptr); });
  SearchResult result = workers_[0]->think(root, limits, info);
  helpers_stop_.store(true, std::memory_order_relaxed);
  for (auto& t : helpers) t.join();
  return result;
}

}  // namespace v3
