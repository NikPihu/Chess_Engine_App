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

bool features::parse(const std::string& text, std::uint32_t& out) {
  struct Name {
    const char* name;
    std::uint32_t bit;
  };
  static const Name names[] = {{"killers", KILLERS}, {"history", HISTORY}, {"pvs", PVS},
                               {"nmp", NULL_MOVE},   {"lmr", LMR},         {"check", CHECK_EXTENSION},
                               {"aspiration", ASPIRATION}, {"rfp", REVERSE_FUTILITY}};
  std::uint32_t all = 0;
  for (const Name& n : names) all |= n.bit;
  std::uint32_t result = 0;
  std::stringstream ss(text);
  std::string item;
  while (std::getline(ss, item, ',')) {
    item.erase(std::remove_if(item.begin(), item.end(), ::isspace), item.end());
    if (item.empty()) continue;
    bool remove = item[0] == '-';
    if (remove) item.erase(0, 1);
    std::uint32_t bit = 0;
    if (item == "all") bit = all;
    else if (item == "none") bit = 0;
    else if (item == "proven") bit = PROVEN;
    else {
      bool found = false;
      for (const Name& n : names)
        if (item == n.name) bit = n.bit, found = true;
      if (!found) return false;
    }
    result = remove ? result & ~bit : result | bit;
  }
  out = result;
  return true;
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

// ---------------------------------------------------------------- search

namespace {

int LMR_TABLE[64][64];

struct LmrInit {
  LmrInit() {
    for (int d = 1; d < 64; d++)
      for (int m = 1; m < 64; m++)
        LMR_TABLE[d][m] = int(0.75 + std::log(double(d)) * std::log(double(m)) / 2.25);
  }
} lmr_init;

constexpr int HASH_MOVE_SCORE = 10'000'000;
constexpr int CAPTURE_SCORE = 1'000'000;
constexpr int PROMOTION_SCORE = 900'000;
constexpr int KILLER_1_SCORE = 800'000;
constexpr int KILLER_2_SCORE = 790'000;
constexpr int HISTORY_MAX = 500'000;
// Indexed by PieceType: pawn, knight, bishop, rook, queen, king.
constexpr int VALUE[6] = {100, 320, 330, 500, 900, 2000};

bool is_quiet(const Position& pos, Move m) {
  return pos.piece_on(move_to(m)) == NO_PIECE && move_flag(m) != FLAG_EN_PASSANT && !is_promotion(m);
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

}  // namespace

Search::Search() { new_game(); }

void Search::new_game() {
  tt_.clear();
  std::memset(killers_, 0, sizeof(killers_));
  std::memset(history_, 0, sizeof(history_));
}

int Search::evaluate_position(const Position& pos) {
  if (!net_) return material_eval(pos);
  nnue::Accumulator a;
  a.refresh(*net_, pos);
  return a.evaluate(*net_, pos.side_to_move());
}

int Search::evaluate(int ply) {
  if (!net_) return material_eval(pos_);
  return acc_[ply].evaluate(*net_, pos_.side_to_move());
}

void Search::make(Move m, int ply) {
  if (net_) acc_[ply + 1].update(*net_, pos_, m, acc_[ply]);
  pos_.make_move(m);
}

void Search::make_null(int ply) {
  if (net_) acc_[ply + 1] = acc_[ply];
  pos_.make_null();
}

void Search::check_limits() {
  if (stop_.load(std::memory_order_relaxed) || (node_limit_ && nodes_ >= node_limit_) ||
      (hard_deadline_ms_ > 0 && now_ms() >= hard_deadline_ms_))
    aborted_ = true;
}

void Search::update_pv(int ply, Move m) {
  pv_[ply][ply] = m;
  int child = pv_length_[ply + 1];
  for (int i = ply + 1; i < child; i++) pv_[ply][i] = pv_[ply + 1][i];
  pv_length_[ply] = std::max(child, ply + 1);
}

void Search::score_moves(const MoveList& list, int* scores, Move hash_move, int ply, bool qs) {
  const Color side = pos_.side_to_move();
  for (int i = 0; i < list.size; i++) {
    Move m = list.moves[i];
    if (m == hash_move) {
      scores[i] = HASH_MOVE_SCORE;
      continue;
    }
    const int flag = move_flag(m);
    const Piece victim = pos_.piece_on(move_to(m));
    int score = 0;
    const bool capture = victim != NO_PIECE || flag == FLAG_EN_PASSANT;
    if (capture) {
      int victim_type = flag == FLAG_EN_PASSANT ? PAWN : type_of(victim);
      int attacker_type = type_of(pos_.piece_on(move_from(m)));
      score = CAPTURE_SCORE + VALUE[victim_type] * 10 - VALUE[attacker_type] / 10;
    }
    if (qs) {
      // v1's phase4.MoveOrdering (quiescence): promotions add on top of any capture score.
      if (is_promotion(m)) score += PROMOTION_SCORE + VALUE[promotion_type(m)];
    } else {
      // v1's phase6.MoveOrdering2: captures, promotions, killers, then history.
      if (!capture) {
        if (is_promotion(m)) score = PROMOTION_SCORE + VALUE[promotion_type(m)];
        else if (on(features::KILLERS) && m == killers_[ply][0]) score = KILLER_1_SCORE;
        else if (on(features::KILLERS) && m == killers_[ply][1]) score = KILLER_2_SCORE;
        else if (on(features::HISTORY)) score = history_[side][move_from(m)][move_to(m)];
      } else if (is_promotion(m)) {
        score += VALUE[promotion_type(m)];
      }
    }
    scores[i] = score;
  }
}

SearchResult Search::think(const Position& root, const SearchLimits& limits, const InfoFn& info) {
  pos_ = root;
  nodes_ = 0;
  aborted_ = false;
  sel_depth_ = 0;
  previous_score_ = 0;
  node_limit_ = limits.nodes;
  tt_.new_search();
  std::memset(killers_, 0, sizeof(killers_));
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
  hard_deadline_ms_ = time.hard_ms() > 0 ? start_ms_ + time.hard_ms() : 0;
  int max_depth = limits.depth > 0 ? std::min(limits.depth, MAX_PLY - 1) : MAX_PLY - 1;

  result.best = root_moves.moves[0];
  for (int depth = 1; depth <= max_depth; depth++) {
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
    if (info) info(info_line(depth, score, elapsed));

    bool mate_found = std::abs(score) >= MATE_THRESHOLD;
    if (mate_found && limits.depth < 0 && depth >= MATE - std::abs(score)) break;
    if (time.stop_after_iteration(elapsed)) break;
  }
  result.nodes = nodes_;
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

int Search::search(int depth, int ply, int alpha, int beta, bool allow_null) {
  pv_length_[ply] = ply;
  if ((nodes_ & 2047) == 0) check_limits();
  if (aborted_) return 0;
  const bool pv_node = beta - alpha > 1;

  if (ply > 0) {
    if (pos_.is_draw(ply)) return 0;
    alpha = std::max(alpha, -MATE + ply);
    beta = std::min(beta, MATE - ply - 1);
    if (alpha >= beta) return alpha;
  }

  const bool in_check = pos_.in_check();
  if (in_check && on(features::CHECK_EXTENSION)) depth++;
  if (depth <= 0) return quiescence(ply, alpha, beta);
  if (ply >= MAX_PLY - 1) return evaluate(ply);
  nodes_++;

  const Key key = pos_.key();
  TTHit hit;
  Move hash_move = MOVE_NONE;
  if (tt_.probe(key, ply, hit)) {
    hash_move = hit.move;
    if (ply > 0 && !pv_node && hit.depth >= depth &&
        (hit.bound == BOUND_EXACT || (hit.bound == BOUND_LOWER && hit.score >= beta) ||
         (hit.bound == BOUND_UPPER && hit.score <= alpha)))
      return hit.score;
  }

  const int static_eval = in_check ? -INFINITE_SCORE : evaluate(ply);

  if (on(features::REVERSE_FUTILITY) && !pv_node && !in_check && depth <= 6 &&
      std::abs(beta) < MATE_THRESHOLD && static_eval - 80 * depth >= beta)
    return static_eval;

  if (on(features::NULL_MOVE) && allow_null && !pv_node && !in_check && depth >= 3 &&
      static_eval >= beta && has_non_pawn_material(pos_)) {
    int reduction = 3 + depth / 6;
    make_null(ply);
    int score = -search(depth - 1 - reduction, ply + 1, -beta, -beta + 1, false);
    pos_.unmake_null();
    if (aborted_) return 0;
    if (score >= beta) return score >= MATE_THRESHOLD ? beta : score;
  }

  MoveList& moves = lists_[ply];
  moves.size = 0;
  generate(pos_, moves);
  if (moves.size == 0) return in_check ? -MATE + ply : 0;
  int* scores = scores_[ply];
  score_moves(moves, scores, hash_move, ply, false);

  const Color side = pos_.side_to_move();
  const int original_alpha = alpha;
  int best_score = -INFINITE_SCORE;
  Move best_move = MOVE_NONE;
  for (int i = 0; i < moves.size; i++) {
    pick_next(moves, scores, i);
    const Move m = moves.moves[i];
    const bool quiet = is_quiet(pos_, m);
    make(m, ply);
    const bool gives_check = pos_.in_check();
    const int new_depth = depth - 1;
    int score;

    if (i == 0) {
      score = -search(new_depth, ply + 1, -beta, -alpha, true);
    } else {
      int reduction = 0;
      if (on(features::LMR) && depth >= 3 && i >= 3 && quiet && !in_check && !gives_check)
        reduction = std::max(0, std::min(LMR_TABLE[std::min(depth, 63)][std::min(i, 63)], new_depth - 1));
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

    if (score > best_score) {
      best_score = score;
      best_move = m;
      if (score > alpha) {
        alpha = score;
        update_pv(ply, m);
        if (alpha >= beta) {
          if (quiet) {
            if (on(features::KILLERS) && killers_[ply][0] != m) {
              killers_[ply][1] = killers_[ply][0];
              killers_[ply][0] = m;
            }
            if (on(features::HISTORY)) {
              int& h = history_[side][move_from(m)][move_to(m)];
              h = std::min(HISTORY_MAX, h + depth * depth);
            }
          }
          break;
        }
      }
    }
  }

  Bound bound = best_score >= beta ? BOUND_LOWER
                : best_score > original_alpha ? BOUND_EXACT
                                              : BOUND_UPPER;
  tt_.store(key, best_move, best_score, depth, bound, ply);
  return best_score;
}

int Search::quiescence(int ply, int alpha, int beta) {
  pv_length_[ply] = ply;
  if ((nodes_ & 2047) == 0) check_limits();
  if (aborted_) return 0;
  nodes_++;
  sel_depth_ = std::max(sel_depth_, ply);
  if (ply >= MAX_PLY - 1) return evaluate(ply);

  const bool in_check = pos_.in_check();
  MoveList& moves = lists_[ply];
  moves.size = 0;
  int best_score;
  if (in_check) {
    // In check there is no "standing pat": every evasion must be considered.
    generate(pos_, moves, ALL);
    if (moves.size == 0) return -MATE + ply;
    best_score = -INFINITE_SCORE;
  } else {
    int stand_pat = evaluate(ply);
    if (stand_pat >= beta) return stand_pat;
    alpha = std::max(alpha, stand_pat);
    best_score = stand_pat;
    generate(pos_, moves, NOISY);
    // Like v1: captures and queen promotions only (under-promotions wait for the main search).
    int n = 0;
    for (int i = 0; i < moves.size; i++) {
      Move m = moves.moves[i];
      if (!is_promotion(m) || promotion_type(m) == QUEEN) moves.moves[n++] = m;
    }
    moves.size = n;
  }
  int* scores = scores_[ply];
  score_moves(moves, scores, MOVE_NONE, ply, true);
  for (int i = 0; i < moves.size; i++) {
    pick_next(moves, scores, i);
    const Move m = moves.moves[i];
    make(m, ply);
    int score = -quiescence(ply + 1, -beta, -alpha);
    pos_.unmake_move(m);
    if (aborted_) return 0;
    if (score > best_score) {
      best_score = score;
      if (score > alpha) {
        alpha = score;
        update_pv(ply, m);
        if (alpha >= beta) break;
      }
    }
  }
  return best_score;
}

std::string Search::info_line(int depth, int score, std::int64_t elapsed_ms) const {
  std::ostringstream s;
  s << "info depth " << depth << " seldepth " << std::max(sel_depth_, depth);
  if (std::abs(score) >= MATE_THRESHOLD) {
    int plies = MATE - std::abs(score);
    int moves = (plies + 1) / 2;
    s << " score mate " << (score > 0 ? moves : -moves);
  } else {
    s << " score cp " << score;
  }
  s << " nodes " << nodes_ << " nps " << (elapsed_ms > 0 ? nodes_ * 1000 / elapsed_ms : nodes_)
    << " hashfull " << tt_.hashfull() << " time " << elapsed_ms << " pv";
  for (int i = 0; i < pv_length_[0]; i++) s << ' ' << move_to_uci(pv_[0][i]);
  return s.str();
}

}  // namespace v3
