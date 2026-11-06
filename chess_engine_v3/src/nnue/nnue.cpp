#include "nnue/nnue.h"

#include <immintrin.h>

#include <algorithm>
#include <cstring>
#include <fstream>
#include <new>
#include <vector>

namespace v3::nnue {

void AlignedI16::reset(std::size_t n) {
  if (data) ::operator delete[](data, std::align_val_t(64));
  data = static_cast<std::int16_t*>(::operator new[](n * sizeof(std::int16_t), std::align_val_t(64)));
  size = n;
  std::memset(data, 0, n * sizeof(std::int16_t));
}

AlignedI16::~AlignedI16() {
  if (data) ::operator delete[](data, std::align_val_t(64));
}

namespace {

/** Sum over i < n of clamp(acc[i], 0, QA)^2 * w[i], with AVX2.
 *  mullo16(c, w) is exact (|c*w| <= 255*127), madd16(c*w, c) adds neighbouring pairs into int32
 *  lanes. Each lane sums only n/8 products (<= 128 * 8.3M < 2^31), then the lanes are added in
 *  64 bits, so no width of network can overflow. */
inline std::int64_t screlu_dot(const std::int16_t* acc, const std::int16_t* w, int n) {
  const __m256i zero = _mm256_setzero_si256();
  const __m256i qa = _mm256_set1_epi16(QA);
  __m256i sum = _mm256_setzero_si256();
  for (int i = 0; i < n; i += 16) {
    __m256i c = _mm256_load_si256(reinterpret_cast<const __m256i*>(acc + i));
    c = _mm256_min_epi16(_mm256_max_epi16(c, zero), qa);
    __m256i weight = _mm256_load_si256(reinterpret_cast<const __m256i*>(w + i));
    sum = _mm256_add_epi32(sum, _mm256_madd_epi16(_mm256_mullo_epi16(c, weight), c));
  }
  alignas(32) std::int32_t lanes[8];
  _mm256_store_si256(reinterpret_cast<__m256i*>(lanes), sum);
  std::int64_t total = 0;
  for (int l = 0; l < 8; l++) total += lanes[l];
  return total;
}

inline void add_column(std::int16_t* v, const std::int16_t* col, int n) {
  for (int i = 0; i < n; i += 16) {
    __m256i a = _mm256_load_si256(reinterpret_cast<const __m256i*>(v + i));
    __m256i b = _mm256_load_si256(reinterpret_cast<const __m256i*>(col + i));
    _mm256_store_si256(reinterpret_cast<__m256i*>(v + i), _mm256_add_epi16(a, b));
  }
}

}  // namespace

int Network::king_state(Color perspective, Square king) const {
  int k = perspective == WHITE ? king : king ^ 56;
  bool mirrored = mirror && file_of(k) >= 4;
  if (mirrored) k ^= 7;
  return bucket_map[k] * 2 + (mirrored ? 1 : 0);
}

int Network::feature_index(Color perspective, int state, Piece p, Square sq) const {
  int relation = color_of(p) == perspective ? 0 : 1;
  int s = perspective == WHITE ? sq : sq ^ 56;
  if (state & 1) s ^= 7;
  return (state >> 1) * FEATURES_PER_BUCKET + relation * 384 + type_of(p) * 64 + s;
}

int Network::evaluate(const std::int16_t* us, const std::int16_t* them, int bucket) const {
  const std::int16_t* w = output_weights.data + std::size_t(bucket) * 2 * hidden;
  std::int64_t sum = screlu_dot(us, w, hidden) + screlu_dot(them, w + hidden, hidden);
  std::int64_t output = sum / QA + output_bias[bucket];  // = float output * QA * QB
  return int(output * SCALE / (std::int64_t(QA) * QB));
}

int Network::evaluate_features(const int* us, int n_us, const int* them, int n_them) const {
  alignas(64) std::int16_t a[2][MAX_HIDDEN];
  const int* lists[2] = {us, them};
  int sizes[2] = {n_us, n_them};
  for (int p = 0; p < 2; p++) {
    std::memcpy(a[p], feature_bias.data, sizeof(std::int16_t) * hidden);
    for (int k = 0; k < sizes[p]; k++) {
      int f = lists[p][k];
      if (f < 0 || f >= FEATURES_PER_BUCKET) continue;
      add_column(a[p], feature_weights.data + std::size_t(f) * hidden, hidden);
    }
  }
  return evaluate(a[0], a[1], 0);
}

std::string Network::describe() const {
  return "(768x" + std::to_string(king_buckets) + (mirror ? " mirrored" : "") + " -> " +
         std::to_string(hidden) + ")x2 -> " + std::to_string(output_buckets);
}

// ---------------------------------------------------------------- file formats

namespace {

struct Reader {
  const char* p;
  const char* end;
  bool ok = true;
  template <typename T>
  T get() {
    T v{};
    if (end - p < std::ptrdiff_t(sizeof(T))) {
      ok = false;
      return v;
    }
    std::memcpy(&v, p, sizeof(T));
    p += sizeof(T);
    return v;
  }
  void bytes(void* out, std::size_t n) {
    if (std::size_t(end - p) < n) {
      ok = false;
      return;
    }
    std::memcpy(out, p, n);
    p += n;
  }
};

}  // namespace

std::shared_ptr<const Network> parse(const char* data, std::size_t size, const std::string& name,
                                     std::string& error) {
  if (size < 12) {
    error = name + " is too small to be a network";
    return nullptr;
  }
  Reader r{data + 4, data + size};
  auto net = std::make_shared<Network>();
  if (std::memcmp(data, "NNUE", 4) == 0) {
    // v2: "NNUE" | version 1 | hidden 256 | ft[768*256] | bias[256] | out[512] | out bias int32
    std::int32_t version = r.get<std::int32_t>(), hidden = r.get<std::int32_t>();
    if (version != 1 || hidden != 256) {
      error = name + ": unsupported v2 network (version " + std::to_string(version) + ", hidden " +
              std::to_string(hidden) + ")";
      return nullptr;
    }
    net->hidden = 256;
  } else if (std::memcmp(data, "NNV3", 4) == 0) {
    // v3: "NNV3" | version 3 | hidden | king buckets | output buckets | mirror | bucket map[64]
    //     | ft[KB*768*H] | bias[H] | out[OB*2H] | out bias int32[OB]
    std::int32_t version = r.get<std::int32_t>();
    net->hidden = r.get<std::int32_t>();
    net->king_buckets = r.get<std::int32_t>();
    net->output_buckets = r.get<std::int32_t>();
    net->mirror = r.get<std::int32_t>() != 0;
    for (int& b : net->bucket_map) b = r.get<std::int32_t>();
    bool shape_ok = r.ok && version == 3 && net->hidden > 0 && net->hidden <= MAX_HIDDEN &&
                    net->hidden % 16 == 0 && net->king_buckets >= 1 && net->king_buckets <= 32 &&
                    net->output_buckets >= 1 && net->output_buckets <= 8;
    for (int b : net->bucket_map) shape_ok = shape_ok && b >= 0 && b < net->king_buckets;
    if (!shape_ok) {
      error = name + ": unsupported or corrupt v3 network header";
      return nullptr;
    }
  } else {
    error = name + " is not a .nnue file (bad magic)";
    return nullptr;
  }

  const std::size_t H = std::size_t(net->hidden);
  const std::size_t ft = std::size_t(net->king_buckets) * FEATURES_PER_BUCKET * H;
  const std::size_t expected = std::size_t(r.p - data) + 2 * ft + 2 * H +
                               2 * std::size_t(net->output_buckets) * 2 * H + 4 * net->output_buckets;
  if (size != expected) {
    error = name + ": wrong size " + std::to_string(size) + ", expected " + std::to_string(expected);
    return nullptr;
  }
  net->feature_weights.reset(ft + H);  // + the all-zero column
  net->feature_bias.reset(H);
  net->output_weights.reset(std::size_t(net->output_buckets) * 2 * H);
  r.bytes(net->feature_weights.data, 2 * ft);
  r.bytes(net->feature_bias.data, 2 * H);
  r.bytes(net->output_weights.data, 2 * net->output_weights.size);
  for (int b = 0; b < net->output_buckets; b++) net->output_bias[b] = r.get<std::int32_t>();
  if (!r.ok) {
    error = name + ": truncated";
    return nullptr;
  }
  return net;
}

std::shared_ptr<const Network> load(const std::string& path, std::string& error) {
  std::ifstream in(path, std::ios::binary);
  if (!in) {
    error = "cannot open " + path;
    return nullptr;
  }
  std::vector<char> bytes((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
  return parse(bytes.data(), bytes.size(), path, error);
}

extern const unsigned char EMBEDDED_NET[];
extern const std::size_t EMBEDDED_NET_SIZE;

std::shared_ptr<const Network> load_embedded(std::string& error) {
  if (EMBEDDED_NET_SIZE == 0) {
    error = "this build has no embedded network";
    return nullptr;
  }
  return parse(reinterpret_cast<const char*>(EMBEDDED_NET), EMBEDDED_NET_SIZE, "<embedded>", error);
}

// ---------------------------------------------------------------- accumulator

Delta delta_for(const Position& pos, Move m) {
  Delta d;
  d.from = move_from(m);
  d.to = move_to(m);
  d.moved = pos.piece_on(d.from);
  const Color us = color_of(d.moved);
  d.placed = is_promotion(m) ? make_piece(us, promotion_type(m)) : d.moved;
  const int flag = move_flag(m);
  if (flag == FLAG_EN_PASSANT) {
    d.sub2_sq = Square(d.to + (us == WHITE ? -8 : 8));
    d.sub2 = pos.piece_on(d.sub2_sq);
  } else if (flag == FLAG_CASTLE) {
    bool kingside = d.to > d.from;
    d.sub2 = d.add2 = make_piece(us, ROOK);
    d.sub2_sq = make_square(kingside ? 7 : 0, rank_of(d.from));
    d.add2_sq = make_square(kingside ? 5 : 3, rank_of(d.from));
  } else if (pos.piece_on(d.to) != NO_PIECE) {
    d.sub2 = pos.piece_on(d.to);
    d.sub2_sq = d.to;
  }
  return d;
}

void Accumulator::refresh_side(const Network& net, const Position& pos, Color persp) {
  const int H = net.hidden;
  king_state[persp] = net.king_state(persp, pos.king_square(persp));
  std::memcpy(v[persp], net.feature_bias.data, sizeof(std::int16_t) * H);
  Bitboard occ = pos.pieces();
  while (occ) {
    Square sq = pop_lsb(occ);
    int f = net.feature_index(persp, king_state[persp], pos.piece_on(sq), sq);
    add_column(v[persp], net.feature_weights.data + std::size_t(f) * H, H);
  }
}

void Accumulator::refresh(const Network& net, const Position& pos) {
  refresh_side(net, pos, WHITE);
  refresh_side(net, pos, BLACK);
}

void Accumulator::update(const Network& net, const Position& after, const Delta& d,
                         const Accumulator& parent) {
  const int H = net.hidden;
  for (int p = 0; p < 2; p++) {
    const Color persp = Color(p);
    const int state = parent.king_state[p];
    // Our king crossed into another bucket or mirror half: every feature index changes.
    if (type_of(d.moved) == KING && color_of(d.moved) == persp &&
        net.king_state(persp, after.king_square(persp)) != state) {
      refresh_side(net, after, persp);
      continue;
    }
    king_state[p] = state;
    auto column = [&](Piece pc, Square sq) {
      int f = pc == NO_PIECE ? net.zero_column() : net.feature_index(persp, state, pc, sq);
      return net.feature_weights.data + std::size_t(f) * H;
    };
    const std::int16_t* s1 = column(d.moved, d.from);
    const std::int16_t* s2 = column(d.sub2, d.sub2_sq);
    const std::int16_t* a1 = column(d.placed, d.to);
    const std::int16_t* a2 = column(d.add2, d.add2_sq);
    const std::int16_t* in = parent.v[p];
    std::int16_t* out = v[p];
    // One fused pass: out = in - s1 - s2 + a1 + a2 (16 lanes of wrapping int16 at a time).
    for (int i = 0; i < H; i += 16) {
      auto ld = [i](const std::int16_t* x) {
        return _mm256_load_si256(reinterpret_cast<const __m256i*>(x + i));
      };
      __m256i r = _mm256_sub_epi16(ld(in), ld(s1));
      r = _mm256_sub_epi16(r, ld(s2));
      r = _mm256_add_epi16(r, ld(a1));
      r = _mm256_add_epi16(r, ld(a2));
      _mm256_store_si256(reinterpret_cast<__m256i*>(out + i), r);
    }
  }
}

}  // namespace v3::nnue
