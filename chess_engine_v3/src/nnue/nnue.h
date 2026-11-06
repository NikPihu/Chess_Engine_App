#pragma once
// NNUE evaluation, step 2 = exactly v2's network (chess_engine_v2/nnue): reads the same .nnue files
// and must give the same integer evaluations (checked against the trainer's golden files).
//
//   768 features --(feature transformer, shared weights)--> 256 per perspective
//   SCReLU on [side to move, opponent] = 512 values --> 1 output; output 1.0 = 400 cp
//
// Feature index (v2's nnue/Features.java):
//   relation * 384 + pieceType * 64 + square
//   relation 0 = the perspective's own piece, 1 = the opponent's; square flipped (^56) for black.
//
// Integers: feature weights x255 (QA), output weights x64 (QB), all int16. An accumulator is the
// bias plus the weight columns of the active features; moves add/subtract a few columns.

#include <cstdint>
#include <memory>
#include <string>

#include "board/position.h"

namespace v3::nnue {

constexpr int FEATURES = 768;
constexpr int HIDDEN = 256;
constexpr int QA = 255;
constexpr int QB = 64;
constexpr int SCALE = 400;

inline int feature_index(Color perspective, Piece p, Square sq) {
  int relation = color_of(p) == perspective ? 0 : 1;
  int s = perspective == WHITE ? sq : sq ^ 56;
  return relation * 384 + type_of(p) * 64 + s;
}

struct Network {
  // One extra all-zero column (index FEATURES) so updates always touch a fixed number of columns.
  alignas(64) std::int16_t feature_weights[(FEATURES + 1) * HIDDEN];
  alignas(64) std::int16_t feature_bias[HIDDEN];
  alignas(64) std::int16_t output_weights[2 * HIDDEN];  // [0,256) side to move, [256,512) opponent
  std::int32_t output_bias;

  /** Centipawns for the side whose hidden layer is {@code us}. */
  int evaluate(const std::int16_t* us, const std::int16_t* them) const;
  /** From feature lists (768 = unused slot) - used by the golden-file test. */
  int evaluate_features(const int* us, int n_us, const int* them, int n_them) const;
};

/** Reads a v2 .nnue file. Returns nullptr and sets {@code error} if it is missing or malformed. */
std::shared_ptr<const Network> load(const std::string& path, std::string& error);
/** The network built into the exe (chess_engine_v2/nets/best.nnue at build time). */
std::shared_ptr<const Network> load_embedded(std::string& error);
std::shared_ptr<const Network> parse(const char* data, std::size_t size, const std::string& name,
                                     std::string& error);

struct Accumulator {
  alignas(64) std::int16_t v[2][HIDDEN];

  void refresh(const Network& net, const Position& pos);
  /** this = parent updated for {@code m}, which is about to be played on {@code pos}. */
  void update(const Network& net, const Position& pos, Move m, const Accumulator& parent);
  int evaluate(const Network& net, Color stm) const { return net.evaluate(v[stm], v[~stm]); }
};

}  // namespace v3::nnue
