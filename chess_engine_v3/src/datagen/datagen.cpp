#include "datagen/datagen.h"

#include <atomic>
#include <cstdio>
#include <cstring>
#include <filesystem>
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
  std::string relabel;
  std::size_t chunk = 50'000;  // label: lines per checkpoint
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

/** Resumable output: data goes to <out>.part; <out>.progress records how much is safely on disk.
 *  Only a finished job is renamed to <out>, so a crash can never leave a file that looks complete. */
struct Checkpoint {
  std::string out, part, progress;
  explicit Checkpoint(const std::string& path) : out(path), part(path + ".part"), progress(path + ".progress") {}

  /** Returns {units done, bytes} from a previous run (0, 0 if none) and truncates the .part file
   *  back to that size, dropping anything written after the last checkpoint. */
  std::pair<std::uint64_t, std::uint64_t> restore() const {
    std::uint64_t done = 0, bytes = 0;
    std::ifstream in(progress);
    if (!(in >> done >> bytes) || !std::filesystem::exists(part)) return {0, 0};
    if (std::filesystem::file_size(part) < bytes) return {0, 0};
    std::filesystem::resize_file(part, bytes);
    return {done, bytes};
  }
  void save(std::uint64_t done, std::uint64_t bytes) const {
    std::string tmp = progress + ".tmp";
    {
      std::ofstream f(tmp, std::ios::trunc);
      f << done << ' ' << bytes << '\n';
    }
    std::filesystem::rename(tmp, progress);
  }
  void finish() const {
    std::filesystem::rename(part, out);
    std::filesystem::remove(progress);
  }
};

int self_play(const Options& o, const std::shared_ptr<const nnue::Network>& net) {
  // Records go to <out>.part; an interrupted run keeps its whole records and continues (games are
  // independent, so nothing is lost but the games that were in progress).
  Checkpoint cp(o.out);
  std::uint64_t have = 0;
  if (std::filesystem::exists(cp.part)) {
    have = std::filesystem::file_size(cp.part) / RECORD_SIZE;
    std::filesystem::resize_file(cp.part, have * RECORD_SIZE);
  }
  Output out;
  out.target = o.positions;
  out.written = have;
  out.file.open(cp.part, std::ios::binary | (have ? std::ios::app : std::ios::trunc));
  if (!out.file) {
    std::fprintf(stderr, "cannot write %s\n", cp.part.c_str());
    return 1;
  }
  if (have) std::fprintf(stderr, "self-play: resuming with %llu positions already written\n", (unsigned long long)have);
  std::fprintf(stderr, "self-play: %llu positions -> %s, %llu nodes, %d threads, net %s\n",
               (unsigned long long)o.positions, o.out.c_str(), (unsigned long long)o.nodes, o.threads,
               net ? net->describe().c_str() : "none");
  const std::int64_t start = now_ms();
  std::vector<std::thread> workers;
  for (int t = 0; t < o.threads; t++) {
    workers.emplace_back([&, t]() {
      auto search = make_searcher(o, net);
      // A resumed run gets new seeds, so it does not replay the games already written.
      std::mt19937_64 rng(o.seed * 1000003ULL + std::uint64_t(t) * 7919ULL + have * 104729ULL);
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
  cp.finish();
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

  // Work in chunks; after each chunk the records are appended and the progress file updated, so
  // after a crash or power loss the job continues from the last finished chunk.
  const std::size_t CHUNK = std::max<std::size_t>(1, o.chunk);
  Checkpoint cp(o.out);
  auto [done, bytes] = cp.restore();
  std::ofstream file(cp.part, std::ios::binary | (done ? std::ios::app : std::ios::trunc));
  if (!file) {
    std::fprintf(stderr, "cannot write %s\n", cp.part.c_str());
    return 1;
  }
  std::fprintf(stderr, "label: %zu positions from %s -> %s, %llu nodes, %d threads%s\n", lines.size(),
               o.in.c_str(), o.out.c_str(), (unsigned long long)o.nodes, o.threads,
               done ? (", resuming after line " + std::to_string(done)).c_str() : "");
  std::atomic<std::uint64_t> skipped{0};
  std::uint64_t written = bytes / RECORD_SIZE;
  const std::int64_t start = now_ms();
  const std::uint64_t first = done;
  std::vector<std::unique_ptr<Search>> searchers;
  for (int t = 0; t < o.threads; t++) searchers.push_back(make_searcher(o, net));

  for (std::size_t chunk = done; chunk < lines.size(); chunk += CHUNK) {
    const std::size_t stop = std::min(lines.size(), chunk + CHUNK);
    std::atomic<std::size_t> next{chunk};
    std::vector<std::vector<char>> buffers(o.threads);
    std::vector<std::thread> workers;
    for (int t = 0; t < o.threads; t++) {
      workers.emplace_back([&, t]() {
        Search& search = *searchers[t];
        std::vector<char>& buf = buffers[t];
        for (std::size_t i; (i = next++) < stop;) {
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
          search.clear_stop();
          SearchResult r = search.think(pos, node_limits(o.nodes), nullptr);
          int white_score = pos.side_to_move() == WHITE ? r.score : -r.score;
          if (r.best == MOVE_NONE || !is_quiet(pos, r.best) || std::abs(r.score) >= MATE_THRESHOLD ||
              std::abs(white_score) >= 3000) {
            skipped++;
            continue;
          }
          int result = result_text.empty() ? 1 : std::clamp(std::atoi(result_text.c_str()), 0, 2);
          int weight = weight_text.empty() ? 1 : std::clamp(std::atoi(weight_text.c_str()), 1, 255);
          buf.resize(buf.size() + RECORD_SIZE);
          write_record(buf.data() + buf.size() - RECORD_SIZE, pos, white_score, result, weight);
        }
      });
    }
    for (auto& w : workers) w.join();
    for (const auto& buf : buffers) {
      file.write(buf.data(), std::streamsize(buf.size()));
      written += buf.size() / RECORD_SIZE;
    }
    file.flush();
    cp.save(stop, written * RECORD_SIZE);
    double secs = std::max(1e-3, (now_ms() - start) / 1000.0);
    std::fprintf(stderr, "label: %zu / %zu lines, %llu records, %.0f lines/s\n", stop, lines.size(),
                 (unsigned long long)written, (stop - first) / secs);
  }
  file.close();
  cp.finish();
  std::fprintf(stderr, "label: wrote %llu records, skipped %llu (check, tactical or mate) in %.0f s\n",
               (unsigned long long)written, (unsigned long long)skipped.load(), (now_ms() - start) / 1000.0);
  return 0;
}

struct EpdLine {
  std::string fen, bm, rest;
};

std::vector<EpdLine> read_epd(const std::string& path, std::vector<std::string>* comments) {
  std::vector<EpdLine> out;
  std::ifstream in(path);
  for (std::string line; std::getline(in, line);) {
    if (!line.empty() && line.back() == '\r') line.pop_back();
    std::size_t bm = line.find(" bm ");
    if (line.empty() || line[0] == '#' || bm == std::string::npos) {
      if (comments && !line.empty()) comments->push_back(line);
      continue;
    }
    std::size_t semi = line.find(';', bm);
    EpdLine e;
    e.fen = line.substr(0, bm);
    e.bm = line.substr(bm + 4, semi == std::string::npos ? std::string::npos : semi - bm - 4);
    e.rest = semi == std::string::npos ? "" : line.substr(semi + 1);
    out.push_back(e);
  }
  return out;
}

/** Blunder suite: positions the Lichess bot got wrong, with the better move ("bm", SAN).
 *  Solved = the engine plays that move at a fixed node budget. With --relabel, the suite is
 *  rewritten with the move our deepest search prefers (so the gate's answers come from the
 *  strongest judge we have, not from the network being tested). */
int blunders(const Options& o, const std::shared_ptr<const nnue::Network>& net, const std::string& relabel) {
  std::vector<std::string> comments;
  std::vector<EpdLine> lines = read_epd(o.in, &comments);
  if (lines.empty()) {
    std::fprintf(stderr, "no positions in %s\n", o.in.c_str());
    return 1;
  }
  std::vector<std::string> found(lines.size());
  std::vector<int> solved(lines.size(), 0);
  std::atomic<std::size_t> next{0};
  std::vector<std::thread> workers;
  for (int t = 0; t < o.threads; t++) {
    workers.emplace_back([&]() {
      auto search = make_searcher(o, net);
      for (std::size_t i; (i = next++) < lines.size();) {
        Position pos;
        if (!pos.set_fen(lines[i].fen)) continue;
        Move bm = parse_san(pos, lines[i].bm);
        search->new_game();
        search->clear_stop();
        SearchLimits limits;
        limits.nodes = o.nodes;
        SearchResult r = search->think(pos, limits, nullptr);
        found[i] = r.best == MOVE_NONE ? "" : move_to_san(pos, r.best);
        solved[i] = bm != MOVE_NONE && r.best == bm;
      }
    });
  }
  for (auto& w : workers) w.join();
  int total = int(lines.size()), ok = 0;
  for (int s : solved) ok += s;
  if (!relabel.empty()) {
    std::ofstream out(relabel, std::ios::trunc);
    for (const std::string& c : comments) out << c << '\n';
    out << "# Re-labelled by chess_engine_v3 at " << o.nodes << " nodes per position (engine_v3 blunders --relabel).\n";
    int changed = 0;
    for (std::size_t i = 0; i < lines.size(); i++) {
      if (found[i].empty()) continue;
      changed += !solved[i];
      out << lines[i].fen << " bm " << found[i] << ";" << lines[i].rest << '\n';
    }
    std::fprintf(stderr, "relabel: %d of %d best moves changed -> %s\n", changed, total, relabel.c_str());
  }
  std::printf("SOLVED %d %d\n", ok, total);
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
    else if (k == "--epd") o.in = v;
    else if (k == "--relabel") o.relabel = v;
    else if (k == "--chunk") o.chunk = std::stoull(v);
    else {
      std::fprintf(stderr, "unknown option %s\n", k.c_str());
      return 2;
    }
  }
  if (mode == "blunders") {
    if (o.in.empty()) {
      std::fprintf(stderr, "usage: engine_v3 blunders --epd suite.epd [--net f.nnue] [--nodes 20000] [--threads N] [--relabel out.epd]\n");
      return 2;
    }
    if (o.nodes == 5000) o.nodes = 20000;
    std::string error;
    auto net = o.net.empty() ? nnue::load_embedded(error) : nnue::load(o.net, error);
    if (!net) {
      std::fprintf(stderr, "network: %s\n", error.c_str());
      return 1;
    }
    return blunders(o, net, o.relabel);
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
