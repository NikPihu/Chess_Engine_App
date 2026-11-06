#pragma once
// Perft: count the leaf nodes of the full legal move tree to a fixed depth. The counts for standard
// positions are published, so any move generation bug shows up as a wrong number.

#include <cstdint>
#include <string>

#include "board/position.h"

namespace v3 {

std::uint64_t perft(Position& pos, int depth);
/** Perft split by root move ("divide"), written to {@code out}; returns the total. */
std::uint64_t perft_divide(Position& pos, int depth, std::string& out);

}  // namespace v3
