"""The self-improvement loop: play -> learn -> prove -> promote, one generation at a time.

  python chess_engine_v2/train/loop.py --generations 3 [--positions 3000000] [--tc 5+0.05]

Generation N:
  1. data    gen 1: self-play by chess_engine_v1's best search (no network exists yet)
             gen N: self-play by the current best network  (the engine learns from itself)
             plus the Lichess bot's real games, re-scored deeper (fetched fresh each time)
  2. train   on the newest two generations of self-play data + Lichess positions (<=15%),
             starting from the best network's weights
  3. prove   SPRT: new network vs the current best (gen 1: vs chess_engine_v1's best search)
  4. promote only if SPRT accepts H1 -> nets/best.nnue; otherwise keep the old best

Everything is logged to docs/results/v2-generations.md and the Elo ledger. The bot on Lichess
is NOT switched automatically; deploying a new best network stays your decision.
"""

import argparse
import os
import shutil
import subprocess
import sys
import time

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
V2 = os.path.join(ROOT, "chess_engine_v2")
NETS = os.path.join(V2, "nets")
DATA = os.path.join(ROOT, "tools", "data")
JAR = os.path.join(ROOT, "tools", "versions", "loop-engine.jar")
LOG = os.path.join(ROOT, "docs", "results", "v2-generations.md")
LEDGER = os.path.join(ROOT, "docs", "results", "elo-ledger.md")


def run(cmd):
    print("\n$ " + " ".join(cmd), flush=True)
    return subprocess.run(cmd, cwd=ROOT).returncode


def fwd(path):
    return path.replace("\\", "/")


def last_ledger_line():
    with open(LEDGER, encoding="utf-8") as f:
        return f.read().strip().splitlines()[-1]


def log(text):
    new = not os.path.exists(LOG)
    with open(LOG, "a", encoding="utf-8") as f:
        if new:
            f.write(
                "# chess_engine_v2 generations\n\n"
                "Each generation learns from self-play of the previous best (plus Lichess games) and is\n"
                "promoted only if SPRT proves it stronger. Written by `chess_engine_v2/train/loop.py`.\n\n"
                "| Gen | Data | Positions | Trained from | Test | Result | Promoted |\n"
                "|---|---|---|---|---|---|---|\n"
            )
        f.write(text + "\n")


def best_generation():
    marker = os.path.join(NETS, "best.txt")
    return int(open(marker).read().strip()) if os.path.exists(marker) else 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--generations", type=int, default=3)
    ap.add_argument("--positions", type=int, default=3_000_000)
    ap.add_argument("--tc", default="5+0.05")
    ap.add_argument("--epochs", type=int, default=15)
    ap.add_argument("--threads", type=int, default=max(1, (os.cpu_count() or 4) - 2))
    args = ap.parse_args()

    # One fixed engine build for the whole loop (networks are passed as files, not rebuilt in).
    os.makedirs(os.path.dirname(JAR), exist_ok=True)
    shutil.copy(os.path.join(V2, "target", "chess_engine_v2.jar"), JAR)
    os.makedirs(NETS, exist_ok=True)

    for _ in range(args.generations):
        best = best_generation()
        gen = best + 1
        while os.path.exists(os.path.join(NETS, f"gen-{gen}.nnue")):
            gen += 1  # a rejected candidate already used this number
        best_net = os.path.join(NETS, f"gen-{best}.nnue") if best else None
        print(f"\n===== Generation {gen} (current best: {'gen-' + str(best) if best else 'v1 search'}) =====")

        # 1. Self-play data from the current best engine.
        data = os.path.join(DATA, f"gen-{gen - 1}.bin")
        if not os.path.exists(data):
            cmd = ["java", "-Xmx4g", "-cp", JAR, "datagen.SelfPlay", "--positions", str(args.positions),
                   "--out", data, "--threads", str(args.threads), "--seed", str(1000 + gen)]
            cmd += ["--engine", "nnue", "--net", best_net] if best_net else ["--engine", "v2"]
            if run(cmd) != 0:
                sys.exit("self-play failed")
        datasets = [data]
        previous = os.path.join(DATA, f"gen-{gen - 2}.bin")
        if gen >= 2 and os.path.exists(previous):
            datasets.append(previous)

        # 1b. Real games from Lichess, re-scored deeper.
        run([sys.executable, os.path.join(V2, "train", "lichess_games.py"), "--out", os.path.join(DATA, "lichess")])
        lichess_bin = os.path.join(DATA, "lichess.bin")
        cmd = ["java", "-Xmx4g", "-cp", JAR, "datagen.PgnPositions", "--pgn", os.path.join(DATA, "lichess"),
               "--out", lichess_bin, "--threads", str(args.threads),
               "--blunders", os.path.join(V2, "tests", "lichess-blunders.epd")]
        cmd += ["--engine", "nnue", "--net", best_net] if best_net else ["--engine", "v2"]
        extras = [lichess_bin + ":0.15"] if run(cmd) == 0 and os.path.getsize(lichess_bin) > 0 else []

        # 2. Train.
        out = os.path.join(NETS, f"gen-{gen}")
        cmd = [sys.executable, os.path.join(V2, "train", "train.py"), "--data", *datasets, "--out", out,
               "--epochs", str(args.epochs)]
        if extras:
            cmd += ["--extra", *extras]
        if best_net and os.path.exists(os.path.join(NETS, f"gen-{best}.pt")):
            cmd += ["--init", os.path.join(NETS, f"gen-{best}.pt")]
        if run(cmd) != 0:
            sys.exit("training failed")

        # 3. Prove it: SPRT against the current best.
        candidate = f"nnue:EvalFile={fwd(out + '.nnue')}"
        base = f"nnue:EvalFile={fwd(best_net)}" if best_net else "v2"
        label = f"v2 gen-{gen} vs " + (f"gen-{best}" if best else "chess_engine_v1 best (v2 search)")
        run(["java", os.path.join("tools", "Sprt.java"), candidate, base, "--tc", args.tc,
             "--maxgames", "4000", "--label", label, "--jar", fwd(JAR)])
        row = last_ledger_line()
        accepted = "H1" in row

        # 4. Promote.
        if accepted:
            shutil.copy(out + ".nnue", os.path.join(NETS, "best.nnue"))
            with open(os.path.join(NETS, "best.txt"), "w") as f:
                f.write(str(gen))
        cells = [c.strip() for c in row.strip("|").split("|")]
        positions = sum(os.path.getsize(d) // 72 for d in datasets)
        log(
            f"| {gen} | {', '.join(os.path.basename(d) for d in datasets)}{' + lichess' if extras else ''}"
            f" | {positions:,} | {'gen-' + str(best) if best else 'random init'}"
            f" | vs {'gen-' + str(best) if best else 'v1 best'} | {cells[6]} W-L-D, Elo {cells[7]}"
            f" | {'✅ yes' if accepted else '❌ no'} |"
        )
        print(f"===== Generation {gen}: {'PROMOTED' if accepted else 'rejected'} =====", flush=True)

    print(f"\nBest network: gen-{best_generation()}  ->  {os.path.join(NETS, 'best.nnue')}")
    print("Rebuild (mvn -q package -DskipTests) to bundle it in chess_engine_v2.jar.")


if __name__ == "__main__":
    main()
