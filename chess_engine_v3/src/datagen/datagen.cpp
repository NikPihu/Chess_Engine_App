#include "datagen/datagen.h"

#include <atomic>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <iostream>
#include <memory>
#include <mutex>
#include <random>
#include <sstream>
#include <thread>

#include "board/position.h"
#include "movegen/movegen.h"
#include "nnue/nnue.h"
#include "search/search.h"

namespace v3::datagen {

namespace {

constexpr int RECORD_SIZE = 72;
constexpr int RANDOM_PLIES = 8;
constexpr int MIN_PLY = 10;
constexpr int MAX_PLIES = 400;

struct Options {
  std::uint64_t positions = 1'000'000;
  std::string out, in, net;
  std::uint64_t nodes = 5000;
  int threads = std::max(1u, std::thread::hardware_concurrency() - 4);
  std::uint64_t seed = 1;
  int hash = 16;
};

/** One training record, laid out exactly like chess_engine_v2/datagen/Record.java. */
void write_record(char* out, const Position& pos, int white_score, int result, int weight) {
  std::uint16_t features[32];
  int n = 0;
  Bitboard occ = pos.pieces();
  while (occ && n < 32) {
    Square sq = pop_lsb(occ);
    features[n++] = std::uint16_t(nnue::v2_feature_index(WHITE, pos.piece_on(sq), sq));
  }
  for (; n < 32; n++) features[n] = 768;
  std::memcpy(out, features, 64);
  std::int16_t score = std::int16_t(std::clamp(white_score, -32000, 32000));
  std::memcpy(out + 64, &score, 2);
  out[66] = char(pos.side_to_move());
  out[67] = char(result);
  out[68] = char(weight);
  out[69] = out[70] = out[71] = 0;
}

class Adjudicator {
 public:
  static constexpr int NONE = -1, BLACK_WINS = 0, DRAW = 1, WHITE_WINS = 2;
  int update(int white_score, bool mate, int ply) {
    if (mate) return white_score > 0 ? WHITE_WINS : BLACK_WINS;
    white_ = white_score >= 1000 ? white_ + 1 : 0;
    black_ = white_score <= -1000 ? black_ + 1 : 0;
    quiet_ = std::abs(white_score) <= 15 ? quiet_ + 1 : 0;
    if (white_ >= 4) return WHITE_WINS;
    if (black_ >= 4) return BLACK_WINS;
    if (ply >= 160 && quiet_ >= 12) return DRAW;
    return NONE;
  }

 private:
  int white_ = 0, black_ = 0, quiet_ = 0;
};

bool is_quiet(const Position& pos, Move m) {
  return pos.piece_on(move_to(m)) == NO_PIECE && move_flag(m) != FLAG_EN_PASSANT && !is_promotion(m);
}

std::unique_ptr<Search> make_searcher(const Options& o, const std::shared_ptr<const nnue::Network>& net) {
  auto s = std::make_unique<Search>();
  s->set_network(net);
  s->set_hash_mb(o.hash);
  return s;
}

SearchLimits node_limits(std::uint64_t nodes) {
  SearchLimits l;
  l.soft_nodes = nodes;
  l.nodes = nodes * 20;  // hard cap: an iteration may overshoot, but never forever
  return l;
}

struct Output {
  std::ofstream file;
  std::mutex mutex;
  std::atomic<std::uint64_t> written{0};
  std::atomic<std::uint64_t> games{0};
  std::uint64_t target = 0;

  /** Appends whole games' records; stops exactly at the target. */
  void write(const std::vector<char>& data) {
    std::lock_guard<std::mutex> lock(mutex);
    std::uint64_t have = written.load();
    if (have >= target) return;
    std::uint64_t n = std::min<std::uint64_t>(data.size() / RECORD_SIZE, target - have);
    file.write(data.data(), std::streamsize(n * RECORD_SIZE));
    written += n;
  }
};

std::vector<char> play_game(Search& search, std::mt19937_64& rng, std::uint64_t nodes) {
  search.new_game();
  Position pos;
  for (int i = 0; i < RANDOM_PLIES; i++) {
    MoveList list;
    generate(pos, list);
    if (list.size == 0) return {};
    pos.make_move(list.moves[rng() % list.size]);
  }
  // Records are written as positions are kept; the game result is filled in at the end.
  std::vector<char> data;
  Adjudicator adjudicator;
  int result = Adjudicator::NONE;
  for (int ply = RANDOM_PLIES; result == Adjudicator::NONE; ply++) {
    MoveList list;
    generate(pos, list);
    if (list.size == 0) {
      result = pos.in_check() ? (pos.side_to_move() == WHITE ? Adjudicator::BLACK_WINS : Adjudicator::WHITE_WINS)
                              : Adjudicator::DRAW;
      break;
    }
    if (pos.is_draw(0) || ply >= MAX_PLIES) {
      result = Adjudicator::DRAW;
      break;
    }
    search.clear_stop();
    SearchResult r = search.think(pos, node_limits(nodes), nullptr);
    if (r.best == MOVE_NONE) {
      result = Adjudicator::DRAW;
      break;
    }
    const bool mate = std::abs(r.score) >= MATE_THRESHOLD;
    const int white_score = pos.side_to_move() == WHITE ? r.score : -r.score;
    if (ply == RANDOM_PLIES && std::abs(white_score) > 400) return {};  // lopsided random opening
    if (ply >= MIN_PLY && is_quiet(pos, r.best) && !mate && std::abs(white_score) < 3000 && !pos.in_check()) {
      data.resize(data.size() + RECORD_SIZE);
      write_record(data.data() + data.size() - RECORD_SIZE, pos, white_score, Adjudicator::DRAW, 1);
    }
    result = adjudicator.update(white_score, mate, ply);
    pos.make_move(r.best);
  }
  for (std::size_t off = 0; off < data.size(); off += RECORD_SIZE) data[off + 67] = char(result);
  return data;
}

void progress(const char* what, const Output& out, std::int64_t start) {
  double secs = std::max(1e-3, (now_ms() - start) / 1000.0);
  std::uint64_t n = out.written.load();
  std::fprintf(stderr, "%s: %llu / %llu positions, %llu games, %.0f pos/s\n", what,
               (unsigned long long)n, (unsigned long long)out.target,
               (unsigned long long)out.games.load(), n / secs);
}

int self_play(const Options& o, const std::shared_ptr<const nnue::Network>& net) {
  Output out;
  out.target = o.positions;
  out.file.open(o.out, std::ios::binary | std::ios::trunc);
  if (!out.file) {
    std::fprintf(stderr, "cannot write %s\n", o.out.c_str());
    return 1;
  }
  std::fprintf(stderr, "self-play: %llu positions -> %s, %llu nodes, %d threads, net %s\n",
               (unsigned long long)o.positions, o.out.c_str(), (unsigned long long)o.nodes, o.threads,
               net ? net->describe().c_str() : "none");
  const std::int64_t start = now_ms();
  std::vector<std::thread> workers;
  for (int t = 0; t < o.threads; t++) {
    workers.emplace_back([&, t]() {
      auto search = make_searcher(o, net);
      std::mt19937_64 rng(o.seed * 1000003ULL + std::uint64_t(t) * 7919ULL);
      while (out.written.load() < out.target) {
        std::vector<char> data = play_game(*search, rng, o.nodes);
        if (!data.empty()) out.write(data);
        out.games++;
      }
    });
  }
  std::thread reporter([&]() {
    while (out.written.load() < out.target) {
      for (int i = 0; i < 100 && out.written.load() < out.target; i++)
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
      progress("self-play", out, start);
    }
  });
  for (auto& w : workers) w.join();
  reporter.join();
  out.file.close();
  progress("done", out, start);
  return 0;
}

int label(const Options& o, const std::shared_ptr<const nnue::Network>& net) {
  std::ifstream in(o.in);
  if (!in) {
    std::fprintf(stderr, "cannot read %s\n", o.in.c_str());
    return 1;
  }
  std::vector<std::string> lines;
  for (std::string line; std::getline(in, line);)
    if (!line.empty()) lines.push_back(line);
  Output out;
  out.target = lines.size();
  out.file.open(o.out, std::ios::binary | std::ios::trunc);
  if (!out.file) {
    std::fprintf(stderr, "cannot write %s\n", o.out.c_str());
    return 1;
  }
  std::fprintf(stderr, "label: %zu positions from %s -> %s, %llu nodes, %d threads\n", lines.size(),
               o.in.c_str(), o.out.c_str(), (unsigned long long)o.nodes, o.threads);
  std::atomic<std::size_t> next{0};
  std::atomic<std::uint64_t> skipped{0};
  const std::int64_t start = now_ms();
  std::vector<std::thread> workers;
  for (int t = 0; t < o.threads; t++) {
    workers.emplace_back([&]() {
      auto search = make_searcher(o, net);
      std::vector<char> rec(RECORD_SIZE);
      for (std::size_t i; (i = next++) < lines.size();) {
        std::stringstream ss(lines[i]);
        std::string fen, result_text, weight_text;
        std::getline(ss, fen, ';');
        std::getline(ss, result_text, ';');
        std::getline(ss, weight_text, ';');
        Position pos;
        MoveList list;
        bool ok = pos.set_fen(fen) && !pos.in_check();
        if (ok) generate(pos, list);
        if (!ok || list.size == 0) {
          skipped++;
          continue;
        }
        search->clear_stop();
        SearchResult r = search->think(pos, node_limits(o.nodes), nullptr);
        int white_score = pos.side_to_move() == WHITE ? r.score : -r.score;
        if (r.best == MOVE_NONE || !is_quiet(pos, r.best) || std::abs(r.score) >= MATE_THRESHOLD ||
            std::abs(white_score) >= 3000) {
          skipped++;
          continue;
        }
        int result = result_text.empty() ? 1 : std::clamp(std::atoi(result_text.c_str()), 0, 2);
        int weight = weight_text.empty() ? 1 : std::clamp(std::atoi(weight_text.c_str()), 1, 255);
        write_record(rec.data(), pos, white_score, result, weight);
        out.write(rec);
        out.games++;
      }
    });
  }
  for (auto& w : workers) w.join();
  out.file.close();
  std::fprintf(stderr, "label: wrote %llu records, skipped %llu (check, tactical or mate) in %.0f s\n",
               (unsigned long long)out.written.load(), (unsigned long long)skipped.load(),
               (now_ms() - start) / 1000.0);
  return 0;
}

}  // namespace

int run(const std::string& mode, const std::vector<std::string>& args) {
  init_all();
  Options o;
  for (std::size_t i = 0; i + 1 < args.size(); i += 2) {
    const std::string& k = args[i];
    const std::string& v = args[i + 1];
    if (k == "--positions") o.positions = std::stoull(v);
    else if (k == "--out") o.out = v;
    else if (k == "--in") o.in = v;
    else if (k == "--net") o.net = v;
    else if (k == "--nodes") o.nodes = std::stoull(v);
    else if (k == "--threads") o.threads = std::max(1, std::stoi(v));
    else if (k == "--seed") o.seed = std::stoull(v);
    else if (k == "--hash") o.hash = std::max(1, std::stoi(v));
    else {
      std::fprintf(stderr, "unknown option %s\n", k.c_str());
      return 2;
    }
  }
  if (o.out.empty() || (mode == "label" && o.in.empty())) {
    std::fprintf(stderr, "usage: engine_v3 datagen --positions N --out file.bin [--nodes 5000] [--threads N] [--seed S] [--net f.nnue]\n"
                         "       engine_v3 label --in fens.txt --out file.bin [--nodes 10000] [--threads N] [--net f.nnue]\n");
    return 2;
  }
  std::string error;
  auto net = o.net.empty() ? nnue::load_embedded(error) : nnue::load(o.net, error);
  if (!net) {
    std::fprintf(stderr, "network: %s\n", error.c_str());
    return 1;
  }
  return mode == "label" ? label(o, net) : self_play(o, net);
}

}  // namespace v3::datagen
