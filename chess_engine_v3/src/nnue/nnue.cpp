#include "nnue/nnue.h"

#include <immintrin.h>

#include <cstring>
#include <fstream>
#include <vector>

namespace v3::nnue {

namespace {

/** Sum over i of clamp(acc[i], 0, QA)^2 * w[i], 256 terms, AVX2.
 *  c*w fits in int16 (|c| <= 255, |w| <= 127 because training clips weights), so: mullo16(c, w),
 *  then madd16 with c sums pairs into int32. Same integers as v2's Java loop, so the same result. */
inline std::int32_t screlu_dot(const std::int16_t* acc, const std::int16_t* w) {
  const __m256i zero = _mm256_setzero_si256();
  const __m256i qa = _mm256_set1_epi16(QA);
  __m256i sum = _mm256_setzero_si256();
  for (int i = 0; i < HIDDEN; i += 16) {
    __m256i c = _mm256_load_si256(reinterpret_cast<const __m256i*>(acc + i));
    c = _mm256_min_epi16(_mm256_max_epi16(c, zero), qa);
    __m256i weight = _mm256_load_si256(reinterpret_cast<const __m256i*>(w + i));
    __m256i cw = _mm256_mullo_epi16(c, weight);
    sum = _mm256_add_epi32(sum, _mm256_madd_epi16(cw, c));
  }
  __m128i s = _mm_add_epi32(_mm256_castsi256_si128(sum), _mm256_extracti128_si256(sum, 1));
  s = _mm_add_epi32(s, _mm_shuffle_epi32(s, _MM_SHUFFLE(1, 0, 3, 2)));
  s = _mm_add_epi32(s, _mm_shuffle_epi32(s, _MM_SHUFFLE(2, 3, 0, 1)));
  return _mm_cvtsi128_si32(s);
}

}  // namespace

int Network::evaluate(const std::int16_t* us, const std::int16_t* them) const {
  std::int64_t sum = std::int64_t(screlu_dot(us, output_weights)) +
                     screlu_dot(them, output_weights + HIDDEN);
  std::int64_t output = sum / QA + output_bias;  // = float output * QA * QB
  return int(output * SCALE / (std::int64_t(QA) * QB));
}

int Network::evaluate_features(const int* us, int n_us, const int* them, int n_them) const {
  alignas(64) std::int16_t a[2][HIDDEN];
  const int* lists[2] = {us, them};
  int sizes[2] = {n_us, n_them};
  for (int p = 0; p < 2; p++) {
    std::memcpy(a[p], feature_bias, sizeof(feature_bias));
    for (int k = 0; k < sizes[p]; k++) {
      int f = lists[p][k];
      if (f < 0 || f >= FEATURES) continue;
      for (int i = 0; i < HIDDEN; i++) a[p][i] = std::int16_t(a[p][i] + feature_weights[f * HIDDEN + i]);
    }
  }
  return evaluate(a[0], a[1]);
}

std::shared_ptr<const Network> parse(const char* data, std::size_t size, const std::string& name,
                                     std::string& error) {
  const std::size_t expected = 12 + 2 * (FEATURES * HIDDEN + HIDDEN + 2 * HIDDEN) + 4;
  if (size < 12 || std::memcmp(data, "NNUE", 4) != 0) {
    error = name + " is not a .nnue file (bad magic)";
    return nullptr;
  }
  std::int32_t version, hidden;
  std::memcpy(&version, data + 4, 4);
  std::memcpy(&hidden, data + 8, 4);
  if (version != 1 || hidden != HIDDEN) {
    error = name + ": unsupported network (version " + std::to_string(version) + ", hidden " +
            std::to_string(hidden) + ")";
    return nullptr;
  }
  if (size != expected) {
    error = name + ": wrong size " + std::to_string(size) + ", expected " + std::to_string(expected);
    return nullptr;
  }
  auto net = std::make_shared<Network>();
  const char* p = data + 12;
  std::memcpy(net->feature_weights, p, 2 * FEATURES * HIDDEN);
  p += 2 * FEATURES * HIDDEN;
  std::memset(net->feature_weights + FEATURES * HIDDEN, 0, 2 * HIDDEN);  // the zero column
  std::memcpy(net->feature_bias, p, 2 * HIDDEN);
  p += 2 * HIDDEN;
  std::memcpy(net->output_weights, p, 4 * HIDDEN);
  p += 4 * HIDDEN;
  std::memcpy(&net->output_bias, p, 4);
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

void Accumulator::refresh(const Network& net, const Position& pos) {
  for (int persp = 0; persp < 2; persp++) {
    std::memcpy(v[persp], net.feature_bias, sizeof(v[persp]));
    Bitboard occ = pos.pieces();
    while (occ) {
      Square sq = pop_lsb(occ);
      const std::int16_t* col =
          net.feature_weights + feature_index(Color(persp), pos.piece_on(sq), sq) * HIDDEN;
      for (int i = 0; i < HIDDEN; i++) v[persp][i] = std::int16_t(v[persp][i] + col[i]);
    }
  }
}

void Accumulator::update(const Network& net, const Position& pos, Move m, const Accumulator& parent) {
  const Square from = move_from(m), to = move_to(m);
  const int flag = move_flag(m);
  const Piece piece = pos.piece_on(from);
  const Color us = color_of(piece);
  const Piece placed = is_promotion(m) ? make_piece(us, promotion_type(m)) : piece;

  Piece sub2 = NO_PIECE, add2 = NO_PIECE;
  Square sub2_sq = A1, add2_sq = A1;
  if (flag == FLAG_EN_PASSANT) {
    sub2_sq = Square(to + (us == WHITE ? -8 : 8));
    sub2 = pos.piece_on(sub2_sq);
  } else if (flag == FLAG_CASTLE) {
    bool kingside = to > from;
    sub2 = add2 = make_piece(us, ROOK);
    sub2_sq = make_square(kingside ? 7 : 0, rank_of(from));
    add2_sq = make_square(kingside ? 5 : 3, rank_of(from));
  } else if (pos.piece_on(to) != NO_PIECE) {
    sub2 = pos.piece_on(to);
    sub2_sq = to;
  }

  for (int persp = 0; persp < 2; persp++) {
    Color c = Color(persp);
    auto column = [&](Piece p, Square sq) {
      int f = p == NO_PIECE ? FEATURES : feature_index(c, p, sq);
      return net.feature_weights + f * HIDDEN;
    };
    const std::int16_t* s1 = column(piece, from);
    const std::int16_t* s2 = column(sub2, sub2_sq);
    const std::int16_t* a1 = column(placed, to);
    const std::int16_t* a2 = column(add2, add2_sq);
    const std::int16_t* in = parent.v[persp];
    std::int16_t* out = v[persp];
    // One fused pass: out = in - s1 - s2 + a1 + a2 (16 lanes of wrapping int16 at a time).
    for (int i = 0; i < HIDDEN; i += 16) {
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
