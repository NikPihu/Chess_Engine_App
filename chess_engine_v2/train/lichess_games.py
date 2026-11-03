"""Downloads the bot's games from Lichess (public API, no token needed) for training.

  python chess_engine_v2/train/lichess_games.py [--user nikpihu] [--out tools/data/lichess]

Only games newer than the last download are fetched (the time of the newest game is remembered
in <out>/last.txt), so running it before every generation just adds the new games.
"""

import argparse
import os
import time
import urllib.request

API = "https://lichess.org/api/games/user/{user}?since={since}&clocks=false&evals=false&opening=false"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--user", default="nikpihu")
    ap.add_argument("--out", default="tools/data/lichess")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    marker = os.path.join(args.out, "last.txt")
    since = int(open(marker).read().strip()) if os.path.exists(marker) else 0

    request = urllib.request.Request(
        API.format(user=args.user, since=since + 1),
        headers={"Accept": "application/x-chess-pgn", "User-Agent": "ChessEngineApp-trainer"},
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        pgn = response.read().decode("utf-8")

    games = pgn.count("[Event ")
    if games == 0:
        print(f"no new games for {args.user}")
        return
    path = os.path.join(args.out, f"{args.user}-{time.strftime('%Y%m%d-%H%M%S')}.pgn")
    with open(path, "w", encoding="utf-8") as f:
        f.write(pgn)
    with open(marker, "w") as f:
        f.write(str(int(time.time() * 1000)))
    print(f"saved {games} new games to {path}")


if __name__ == "__main__":
    main()
