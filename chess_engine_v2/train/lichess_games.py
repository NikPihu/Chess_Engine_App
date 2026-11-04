"""Downloads the bot's games from Lichess (public API, no token needed) for training.

  python chess_engine_v2/train/lichess_games.py [--user nikpihu] [--out tools/data/lichess]

Only games newer than the last download are fetched (the time of the newest game is remembered
in <out>/last.txt), so running it before every generation just adds the new games.
"""

import argparse
import datetime
import glob
import json
import os
import re
import time
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
REGISTRY = os.path.join(ROOT, "chess_engine_v2", "nets", "registry.json")
REPORT = os.path.join(ROOT, "docs", "results", "lichess-by-generation.md")

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
    else:
        path = os.path.join(args.out, f"{args.user}-{time.strftime('%Y%m%d-%H%M%S')}.pgn")
        with open(path, "w", encoding="utf-8") as f:
            f.write(pgn)
        with open(marker, "w") as f:
            f.write(str(int(time.time() * 1000)))
        print(f"saved {games} new games to {path}")
    write_report(args.out, args.user)


def live_generation(events, when):
    """Which network the bot used at time `when` ('v1' before chess_engine_v2 went live on Lichess)."""
    live = "v1"
    for e in events:
        if e["event"] in ("lichess-deploy", "promote", "rollback") and e["time"] <= when:
            if e["event"] == "lichess-deploy" or live != "v1":
                live = f"gen-{e['gen']}"
    return live


def write_report(folder, user):
    """W/D/L of the bot's Lichess games, split by the generation that was live."""
    if not os.path.exists(REGISTRY):
        return
    events = json.load(open(REGISTRY, encoding="utf-8"))["events"]
    stats = {}
    for path in glob.glob(os.path.join(folder, "*.pgn")):
        text = open(path, encoding="utf-8").read()
        for game in text.split("[Event ")[1:]:
            tags = dict(re.findall(r'\[(\w+) "([^"]*)"\]', "[Event " + game))
            if "UTCDate" not in tags or "Result" not in tags:
                continue
            when_utc = datetime.datetime.strptime(tags["UTCDate"] + " " + tags.get("UTCTime", "00:00:00"), "%Y.%m.%d %H:%M:%S")
            when = when_utc.replace(tzinfo=datetime.timezone.utc).astimezone().replace(tzinfo=None).isoformat(timespec="seconds")
            gen = live_generation(events, when)
            white = tags.get("White", "").lower() == user.lower()
            res = tags["Result"]
            outcome = "draw" if res == "1/2-1/2" else ("win" if (res == "1-0") == white else "loss")
            s = stats.setdefault(gen, {"win": 0, "draw": 0, "loss": 0})
            s[outcome] += 1
    lines = ["# Lichess results by generation", "",
             f"The bot ({user}) on lichess.org, split by which network was live when each game started.",
             "Written by `chess_engine_v2/train/lichess_games.py`.", "",
             "| Engine | Games | Wins | Draws | Losses | Score |", "|---|---|---|---|---|---|"]
    for gen, s in sorted(stats.items(), key=lambda kv: (kv[0] != "v1", kv[0])):
        n = sum(s.values())
        score = 100 * (s["win"] + 0.5 * s["draw"]) / n if n else 0
        lines.append(f"| {gen} | {n} | {s['win']} | {s['draw']} | {s['loss']} | {score:.1f}% |")
    os.makedirs(os.path.dirname(REPORT), exist_ok=True)
    with open(REPORT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
