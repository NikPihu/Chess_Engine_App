// PGN -> training positions, for the Lichess-elite pipeline:
//
//   engine_v3.exe pgn --in lichess_elite_2024-01.pgn --out tools/data/elite-2024-01.txt
//                     [--min-elo 2200] [--skip-plies 16] [--every 3] [--max-games 0]
//
// Reads every game, keeps those where both players are rated >= min-elo (standard chess only, no
// custom start positions), replays the moves, and writes every `every`-th position after the
// first `skip-plies` plies as "fen;result;1" (result from white's view: 0 loss, 1 draw, 2 win).
//
// Only the moves and the result are used. Comments - including Lichess's "[%eval ...]" annotations,
// which come from Stockfish - are skipped, so no engine's opinion enters our data: the positions
// are labelled afterwards by our own search (engine_v3.exe label).

#include <cctype>
#include <cstdio>
#include <fstream>
#include <string>
#include <vector>

#include "board/position.h"
#include "datagen/datagen.h"
#include "movegen/movegen.h"

namespace v3::datagen {

namespace {

std::string strip_annotations(std::string san) {
  while (!san.empty() && (san.back() == '+' || san.back() == '#' || san.back() == '!' || san.back() == '?'))
    san.pop_back();
  return san;
}

}  // namespace

/** Standard algebraic notation of a legal move in {@code pos} (without check marks). */
std::string move_to_san(const Position& pos, Move m) {
  const Square from = move_from(m), to = move_to(m);
  const Piece piece = pos.piece_on(from);
  const PieceType type = type_of(piece);
  if (move_flag(m) == FLAG_CASTLE) return to > from ? "O-O" : "O-O-O";
  const bool capture = pos.piece_on(to) != NO_PIECE || move_flag(m) == FLAG_EN_PASSANT;
  std::string san;
  if (type == PAWN) {
    if (capture) san += char('a' + file_of(from));
  } else {
    san += "PNBRQK"[type];
    // Disambiguation: another piece of the same type that can also go to `to`.
    MoveList list;
    generate(pos, list);
    bool same_file = false, same_rank = false, ambiguous = false;
    for (Move o : list) {
      if (o == m || move_to(o) != to || pos.piece_on(move_from(o)) != piece) continue;
      ambiguous = true;
      if (file_of(move_from(o)) == file_of(from)) same_file = true;
      if (rank_of(move_from(o)) == rank_of(from)) same_rank = true;
    }
    if (ambiguous) {
      if (!same_file) san += char('a' + file_of(from));
      else if (!same_rank) san += char('1' + rank_of(from));
      else san += square_name(from);
    }
  }
  if (capture) san += 'x';
  san += square_name(to);
  if (is_promotion(m)) {
    san += '=';
    san += "PNBRQK"[promotion_type(m)];
  }
  return san;
}

Move parse_san(const Position& pos, const std::string& text) {
  std::string san = strip_annotations(text);
  if (san == "0-0") san = "O-O";
  if (san == "0-0-0") san = "O-O-O";
  MoveList list;
  generate(pos, list);
  for (Move m : list)
    if (move_to_san(pos, m) == san) return m;
  return MOVE_NONE;
}

namespace {

struct Game {
  std::string white_elo, black_elo, result, variant, setup;
  std::string movetext;
};

/** Splits PGN movetext into SAN tokens, skipping comments, variations, NAGs and move numbers. */
std::vector<std::string> san_tokens(const std::string& text) {
  std::vector<std::string> out;
  int brace = 0, paren = 0;
  std::string tok;
  auto flush = [&]() {
    if (tok.empty()) return;
    std::string t = tok;
    tok.clear();
    // "12." or "12..." prefixes, results, NAGs
    std::size_t dot = t.find_last_of('.');
    if (dot != std::string::npos) t = t.substr(dot + 1);
    if (t.empty() || t[0] == '$' || t == "1-0" || t == "0-1" || t == "1/2-1/2" || t == "*") return;
    if (std::isdigit(static_cast<unsigned char>(t[0])) && t.find_first_not_of("0123456789") == std::string::npos) return;
    out.push_back(t);
  };
  for (std::size_t i = 0; i < text.size(); i++) {
    char c = text[i];
    if (brace) {
      if (c == '}') brace = 0;
      continue;
    }
    if (c == '{') {
      flush();
      brace = 1;
    } else if (c == ';') {  // comment to end of line
      flush();
      while (i < text.size() && text[i] != '\n') i++;
    } else if (c == '(') {
      flush();
      paren++;
    } else if (c == ')') {
      flush();
      if (paren) paren--;
    } else if (paren) {
      continue;
    } else if (std::isspace(static_cast<unsigned char>(c))) {
      flush();
    } else {
      tok += c;
    }
  }
  flush();
  return out;
}

std::string tag_value(const std::string& line) {
  std::size_t a = line.find('"'), b = line.rfind('"');
  return a != std::string::npos && b > a ? line.substr(a + 1, b - a - 1) : "";
}

}  // namespace

int pgn_to_positions(const std::vector<std::string>& args) {
  init_all();
  std::string in_path, out_path;
  int min_elo = 2200, skip_plies = 16, every = 3;
  long long max_games = 0;
  for (std::size_t i = 0; i + 1 < args.size(); i += 2) {
    if (args[i] == "--in") in_path = args[i + 1];
    else if (args[i] == "--out") out_path = args[i + 1];
    else if (args[i] == "--min-elo") min_elo = std::stoi(args[i + 1]);
    else if (args[i] == "--skip-plies") skip_plies = std::stoi(args[i + 1]);
    else if (args[i] == "--every") every = std::max(1, std::stoi(args[i + 1]));
    else if (args[i] == "--max-games") max_games = std::stoll(args[i + 1]);
    else {
      std::fprintf(stderr, "unknown option %s\n", args[i].c_str());
      return 2;
    }
  }
  std::ifstream in(in_path);
  std::ofstream out(out_path, std::ios::trunc);
  if (!in || !out) {
    std::fprintf(stderr, "usage: engine_v3 pgn --in games.pgn --out positions.txt [--min-elo 2200] [--skip-plies 16] [--every 3]\n");
    return 2;
  }
  long long games = 0, kept_games = 0, positions = 0, bad = 0;
  Game g;
  bool in_moves = false;
  auto finish = [&]() {
    if (g.movetext.empty()) return;
    games++;
    int we = std::atoi(g.white_elo.c_str()), be = std::atoi(g.black_elo.c_str());
    int result = g.result == "1-0" ? 2 : g.result == "0-1" ? 0 : g.result == "1/2-1/2" ? 1 : -1;
    bool standard = (g.variant.empty() || g.variant == "Standard") && g.setup != "1";
    if (result >= 0 && standard && we >= min_elo && be >= min_elo) {
      Position pos;
      int ply = 0;
      bool ok = true;
      for (const std::string& san : san_tokens(g.movetext)) {
        Move m = parse_san(pos, san);
        if (m == MOVE_NONE) {
          ok = false;
          break;
        }
        pos.make_move(m);
        ply++;
        if (ply >= skip_plies && (ply - skip_plies) % every == 0 && !pos.in_check()) {
          out << pos.fen() << ';' << result << ";1\n";
          positions++;
        }
      }
      if (ok) kept_games++;
      else bad++;
    }
    g = Game{};
  };
  std::string line;
  while (std::getline(in, line)) {
    if (!line.empty() && line.back() == '\r') line.pop_back();
    if (!line.empty() && line[0] == '[') {
      if (in_moves) {
        finish();
        in_moves = false;
        if (max_games && games >= max_games) break;
      }
      if (line.rfind("[WhiteElo ", 0) == 0) g.white_elo = tag_value(line);
      else if (line.rfind("[BlackElo ", 0) == 0) g.black_elo = tag_value(line);
      else if (line.rfind("[Result ", 0) == 0) g.result = tag_value(line);
      else if (line.rfind("[Variant ", 0) == 0) g.variant = tag_value(line);
      else if (line.rfind("[SetUp ", 0) == 0) g.setup = tag_value(line);
    } else if (!line.empty()) {
      in_moves = true;
      g.movetext += line + ' ';
    }
  }
  finish();
  std::fprintf(stderr, "pgn: %lld games read, %lld used (both >= %d), %lld unreadable, %lld positions -> %s\n",
               games, kept_games, min_elo, bad, positions, out_path.c_str());
  return 0;
}

}  // namespace v3::datagen
