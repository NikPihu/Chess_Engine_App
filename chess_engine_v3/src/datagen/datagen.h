#pragma once
// Training-data generation for v3 networks, in the same 72-byte record format as v2
// (chess_engine_v2/datagen/Record.java), so every old data file stays usable.
//
//   engine_v3.exe datagen --positions 3000000 --out tools/data/v3-selfplay-1.bin
//                         [--nodes 5000] [--threads 12] [--seed 1] [--net file.nnue] [--hash 16]
//       Self-play. Each game starts with 8 random plies (variety), then the engine plays with a
//       soft node limit (finish the iteration that crosses `nodes`, hard stop at 20x). Kept: quiet
//       positions (not in check, best move not a capture/promotion, no mate score, |score| < 3000,
//       at least 10 plies in). Games are adjudicated like v2 (win: |score| >= 1000 for 4 plies or a
//       mate; draw: after ply 160, |score| <= 15 for 12 plies).
//
//   engine_v3.exe label --in positions.txt --out tools/data/elite.bin [--nodes 10000] [--threads 12]
//       Re-labels given positions with our own search ("in-house analysis"): each input line is
//       "fen;result;weight" (result from white's view: 0 loss, 1 draw, 2 win; weight optional).
//       Used for Lichess-elite human games: their positions, our evaluations.
//
// Record: 32 x uint16 features (white's view, v2 index, 768 = unused) | int16 score (white's view)
//         | uint8 side to move | uint8 result | uint8 weight | 3 reserved.

#include <string>
#include <vector>

namespace v3::datagen {

/** Entry point for "datagen" / "label"; args are the words after the command. Returns exit code. */
int run(const std::string& mode, const std::vector<std::string>& args);

}  // namespace v3::datagen
