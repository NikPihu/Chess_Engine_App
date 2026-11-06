#pragma once
// NNUE evaluation.
//
// One general layout covers both network generations:
//
//   (768 x KB king-bucket features --> H) x 2 perspectives --SCReLU--> 2H --> OB output heads
//
//   v2 networks ("NNUE" files): KB = 1, no mirroring, H = 256, OB = 1   (chess_engine_v2/nnue)
//   v3 networks ("NNV3" files): KB = 4, horizontal mirroring, H = 768, OB = 8 (step 5)
//
// Feature index for perspective P (the side whose view this is):
//   1. orient: squares flipped vertically (^56) for black, so each side sees itself at the bottom
//   2. mirror (v3): if P's king stands on files e-h, mirror the board left-right (^7), so the
//      network only has to learn king-on-the-queenside positions (half the patterns, same skill)
//   3. king bucket = BUCKET_MAP[P's oriented, mirrored king square]: separate weights depending on
//      where our king is (castled short/long, still central, walked up the board)
//   index = bucket * 768 + relation * 384 + pieceType * 64 + square
//   relation 0 = P's own piece, 1 = the opponent's.
// Output head = (number of pieces - 2) / 4: opening, middlegame and endgame get their own last
// layer (the network's version of tapered evaluation).
//
// Integers: feature weights x255 (QA), output weights x64 (QB), all int16 (training clips weights
// to +-1.98, so |output weight| <= 127 and c*w fits in 16 bits).

#include <algorithm>
#include <cstdint>
#include <memory>
#include <string>

#include "board/position.h"

namespace v3::nnue {

constexpr int QA = 255;
constexpr int QB = 64;
constexpr int SCALE = 400;
constexpr int MAX_HIDDEN = 1024;
constexpr int FEATURES_PER_BUCKET = 768;

/** v2-style index without buckets or mirroring (used by v2's golden files and data). */
inline int v2_feature_index(Color perspective, Piece p, Square sq) {
  int relation = color_of(p) == perspective ? 0 : 1;
  int s = perspective == WHITE ? sq : sq ^ 56;
  return relation * 384 + type_of(p) * 64 + s;
}

/** Owned, 64-byte aligned int16 array (AVX2 loads need 32-byte alignment). */
struct AlignedI16 {
  std::int16_t* data = nullptr;
  std::size_t size = 0;
  AlignedI16() = default;
  explicit AlignedI16(std::size_t n) { reset(n); }
  ~AlignedI16();
  /** Frees the old buffer and allocates n zeroed values. */
  void reset(std::size_t n);
  AlignedI16(const AlignedI16&) = delete;
  AlignedI16& operator=(const AlignedI16&) = delete;
};

struct Network {
  int hidden = 256;
  int king_buckets = 1;
  int output_buckets = 1;
  bool mirror = false;
  int bucket_map[64] = {};  // oriented (+ mirrored) king square -> bucket

  AlignedI16 feature_weights;  // [(king_buckets * 768 + 1) * hidden], last column all zero
  AlignedI16 feature_bias;     // [hidden]
  AlignedI16 output_weights;   // [output_buckets][2 * hidden]: side to move, then opponent
  std::int32_t output_bias[8] = {};

  int zero_column() const { return king_buckets * FEATURES_PER_BUCKET; }
  int output_bucket(int piece_count) const {
    if (output_buckets == 1) return 0;
    return std::min(output_buckets - 1, std::max(0, (piece_count - 2) / 4));
  }
  /** "King state" of a perspective: bucket * 2 + mirrored. A change forces a refresh. */
  int king_state(Color perspective, Square king) const;
  int feature_index(Color perspective, int king_state, Piece p, Square sq) const;

  /** Centipawns for the side whose hidden layer is {@code us}. */
  int evaluate(const std::int16_t* us, const std::int16_t* them, int bucket) const;
  /** From v2 feature lists (768 = unused slot): only valid for v2-layout networks. */
  int evaluate_features(const int* us, int n_us, const int* them, int n_them) const;
  std::string describe() const;
};

/** Reads a .nnue file (either format). Returns nullptr and sets {@code error} on failure. */
std::shared_ptr<const Network> load(const std::string& path, std::string& error);
/** The network built into the exe. */
std::shared_ptr<const Network> load_embedded(std::string& error);
std::shared_ptr<const Network> parse(const char* data, std::size_t size, const std::string& name,
                                     std::string& error);

/** What a move changes on the board, captured before the move is played. */
struct Delta {
  Piece moved = NO_PIECE, placed = NO_PIECE;
  Square from = A1, to = A1;
  Piece sub2 = NO_PIECE, add2 = NO_PIECE;  // captured piece / castling rook
  Square sub2_sq = A1, add2_sq = A1;
};
Delta delta_for(const Position& pos, Move m);

struct Accumulator {
  alignas(64) std::int16_t v[2][MAX_HIDDEN];
  int king_state[2] = {-1, -1};

  void refresh(const Network& net, const Position& pos);
  void refresh_side(const Network& net, const Position& pos, Color perspective);
  /** this = parent updated for a move already played on {@code after}, described by {@code d}.
   *  A perspective whose king changed bucket or mirror side is rebuilt from scratch. */
  void update(const Network& net, const Position& after, const Delta& d, const Accumulator& parent);
  int evaluate(const Network& net, const Position& pos) const {
    Color stm = pos.side_to_move();
    return net.evaluate(v[stm], v[~stm], net.output_bucket(popcount(pos.pieces())));
  }
};

}  // namespace v3::nnue
