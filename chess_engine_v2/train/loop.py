"""Trains the next generation of chess_engine_v2's network, proves it, and promotes it.

  tools\\train                    one generation (gen = last number + 1), then stop
  tools\\train --generations 3    several in a row
  tools\\train --status           every generation: parent, data, results, which one is live
  tools\\train --rollback 2       make gen-2 the live network again
  tools\\train --stop             stop a running generation (and everything it started)

One generation:
  1. PLAY     the current best network plays itself (3M positions) + new Lichess games are
              downloaded and re-scored deeper
  2. LEARN    train on that data + the parent's newest data, starting from the parent's weights
  3. GATE     the bot's past Lichess blunders: must solve about as many as the current best
  4. PROVE    SPRT match vs the current best
  5. PROMOTE  only on SPRT H1: best.nnue is replaced atomically -> the Lichess bot uses the new
              generation from its next game (no restart needed)

Every generation, promotion and rollback is recorded in chess_engine_v2/nets/registry.json, so the
numbering continues forever and any generation can be brought back.
"""

import argparse
import datetime
import json
import os
import shutil
import subprocess
import sys
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
V2 = os.path.join(ROOT, "chess_engine_v2")
NETS = os.path.join(V2, "nets")
DATA = os.path.join(ROOT, "tools", "data")
REGISTRY = os.path.join(NETS, "registry.json")
BEST = os.path.join(NETS, "best.nnue")
JAR = os.path.join(ROOT, "tools", "versions", "loop-engine.jar")
PIDFILE = os.path.join(DATA, "train.pid")
GENERATIONS_MD = os.path.join(ROOT, "docs", "results", "v2-generations.md")
LEDGER = os.path.join(ROOT, "docs", "results", "elo-ledger.md")
BLUNDERS = os.path.join(V2, "tests", "lichess-blunders.epd")
JAVA = ["java", "-Xmx4g", "--add-modules", "jdk.incubator.vector"]

# Below-normal priority: training never slows down the Lichess bot (children inherit it).
LOW_PRIORITY = getattr(subprocess, "BELOW_NORMAL_PRIORITY_CLASS", 0)


def now():
    return datetime.datetime.now().isoformat(timespec="seconds")


def fwd(path):
    return path.replace("\\", "/")


# ---------------------------------------------------------------- registry

def load_registry():
    with open(REGISTRY, encoding="utf-8") as f:
        return json.load(f)


def save_registry(reg):
    tmp = REGISTRY + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(reg, f, indent=2)
    os.replace(tmp, REGISTRY)
    write_generations_md(reg)


def gen_entry(reg, n):
    return next((g for g in reg["generations"] if g["gen"] == n), None)


def next_number(reg):
    used = [g["gen"] for g in reg["generations"]]
    for name in os.listdir(NETS):
        if name.startswith("gen-") and name.endswith(".nnue"):
            used.append(int(name[4:-5]))
    return max(used, default=0) + 1


def make_live(reg, n, event):
    """Atomically replace best.nnue with gen-n (the bot's next game uses it)."""
    src = os.path.join(NETS, f"gen-{n}.nnue")
    shutil.copy(src, BEST + ".tmp")
    os.replace(BEST + ".tmp", BEST)
    with open(os.path.join(NETS, "best.txt"), "w") as f:
        f.write(str(n))
    reg["best"] = n
    reg["events"].append({"time": now(), "event": event, "gen": n, "lichess": lichess_ratings()})
    save_registry(reg)


def lichess_ratings(user="nikpihu"):
    try:
        req = urllib.request.Request(f"https://lichess.org/api/user/{user}", headers={"User-Agent": "ChessEngineApp-trainer"})
        with urllib.request.urlopen(req, timeout=20) as r:
            perfs = json.load(r).get("perfs", {})
        return {k: perfs[k]["rating"] for k in ("bullet", "blitz", "rapid") if k in perfs}
    except Exception:
        return {}


def write_generations_md(reg):
    lines = [
        "# chess_engine_v2 generations",
        "",
        "Generated from `chess_engine_v2/nets/registry.json` by `train/loop.py`. A generation goes live",
        "(becomes `best.nnue`, used by the Lichess bot from its next game) only after SPRT H1 vs the",
        "current best.",
        "",
        f"**Live now: gen-{reg['best']}**",
        "",
        "| Gen | Parent | Training data | Positions | Blunder suite | Test | Result | Promoted |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for g in reg["generations"]:
        for t in g.get("tests", []) or [{}]:
            lines.append(
                f"| {g['gen']} | {g.get('parent') if g.get('parent') is not None else 'v1 data'} "
                f"| {', '.join(g.get('data', []))} | {g.get('positions', 0):,} | {g.get('blunders', '—')} "
                f"| {t.get('vs', '—')} | {t.get('result', '—')} {t.get('note', '')} "
                f"| {'✅' if g.get('promoted') else '❌'} |"
            )
    lines += ["", "## Events (what was live when)", "", "| Time | Event | Gen | Lichess ratings |", "|---|---|---|---|"]
    for e in reg["events"]:
        lines.append(f"| {e['time']} | {e['event']} | {e['gen']} | {e.get('lichess', {})} |")
    with open(GENERATIONS_MD, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


# ---------------------------------------------------------------- commands

def status():
    reg = load_registry()
    print(f"Live network: gen-{reg['best']}  ({BEST})\n")
    for g in reg["generations"]:
        tests = "; ".join(f"vs {t['vs']}: {t['result']}" for t in g.get("tests", []))
        print(f"gen-{g['gen']:<3} parent={g.get('parent')!s:<5} {'PROMOTED' if g.get('promoted') else 'rejected':<9} {tests}")
    print("\nEvents:")
    for e in reg["events"]:
        print(f"  {e['time']}  {e['event']:<9} gen-{e['gen']}  lichess {e.get('lichess', {})}")


def rollback(n):
    reg = load_registry()
    if not os.path.exists(os.path.join(NETS, f"gen-{n}.nnue")):
        sys.exit(f"gen-{n}.nnue does not exist")
    make_live(reg, n, "rollback")
    print(f"gen-{n} is live again; the Lichess bot uses it from its next game.")


def stop():
    if not os.path.exists(PIDFILE):
        print("no generation is running")
        return
    pid = open(PIDFILE).read().strip()
    subprocess.run(["taskkill", "/PID", pid, "/T", "/F"], capture_output=True)
    os.remove(PIDFILE)
    print(f"stopped the running generation (process tree {pid})")


def run(cmd):
    print("\n$ " + " ".join(cmd), flush=True)
    return subprocess.run(cmd, cwd=ROOT, creationflags=LOW_PRIORITY).returncode


def blunder_score(net, threads):
    if not os.path.exists(BLUNDERS):
        return None
    out = subprocess.run(
        JAVA + ["-cp", JAR, "datagen.BlunderSuite", "--net", net, "--epd", BLUNDERS, "--threads", str(threads)],
        cwd=ROOT, capture_output=True, text=True, creationflags=LOW_PRIORITY,
    ).stdout.strip().splitlines()
    last = [l for l in out if l.startswith("SOLVED")]
    if not last:
        return None
    _, solved, total = last[-1].split()
    return int(solved), int(total)


def one_generation(args):
    reg = load_registry()
    parent = reg["best"]
    parent_net = os.path.join(NETS, f"gen-{parent}.nnue")
    gen = next_number(reg)
    print(f"\n===== Training gen-{gen} (parent: gen-{parent}, the live network) =====", flush=True)

    # 1. PLAY: self-play by the live network.
    data = os.path.join(DATA, f"selfplay-gen{parent}-for-gen{gen}.bin")
    if not os.path.exists(data):
        if run(JAVA + ["-cp", JAR, "datagen.SelfPlay", "--engine", "nnue", "--net", parent_net,
                       "--positions", str(args.positions), "--out", data, "--threads", str(args.threads),
                       "--seed", str(1000 + gen)]) != 0:
            sys.exit("self-play failed")
    parent_entry = gen_entry(reg, parent) or {}
    datasets = [data] + [os.path.join(DATA, d) for d in parent_entry.get("data", [])[:1] if os.path.exists(os.path.join(DATA, d))]

    run([sys.executable, os.path.join(V2, "train", "lichess_games.py"), "--out", os.path.join(DATA, "lichess")])
    lichess_bin = os.path.join(DATA, "lichess.bin")
    extras = []
    if run(JAVA + ["-cp", JAR, "datagen.PgnPositions", "--pgn", os.path.join(DATA, "lichess"),
                   "--out", lichess_bin, "--threads", str(args.threads), "--blunders", BLUNDERS,
                   "--engine", "nnue", "--net", parent_net]) == 0 and os.path.getsize(lichess_bin) > 0:
        extras = [lichess_bin + ":0.15"]

    # 2. LEARN.
    out = os.path.join(NETS, f"gen-{gen}")
    cmd = [sys.executable, os.path.join(V2, "train", "train.py"), "--data", *datasets, "--out", out,
           "--epochs", str(args.epochs), "--threads", str(args.threads)]
    if extras:
        cmd += ["--extra", *extras]
    if os.path.exists(os.path.join(NETS, f"gen-{parent}.pt")):
        cmd += ["--init", os.path.join(NETS, f"gen-{parent}.pt")]
    if run(cmd) != 0:
        sys.exit("training failed")

    entry = {
        "gen": gen, "parent": parent, "date": now(),
        "data": [os.path.basename(d) for d in datasets] + (["lichess.bin"] if extras else []),
        "positions": sum(os.path.getsize(d) // 72 for d in datasets),
        "tests": [], "promoted": False,
    }
    reg["generations"].append(entry)
    save_registry(reg)

    # 3. GATE: the bot's real past blunders.
    cand, best = blunder_score(out + ".nnue", args.threads), blunder_score(parent_net, args.threads)
    if cand and best:
        entry["blunders"] = f"{cand[0]}/{cand[1]} (gen-{parent}: {best[0]})"
        save_registry(reg)
        tolerance = max(2, best[1] * 3 // 100)
        if cand[0] < best[0] - tolerance:
            entry["tests"].append({"vs": f"gen-{parent} blunder suite", "result": "failed gate", "note": ""})
            save_registry(reg)
            print(f"===== gen-{gen}: failed the blunder gate ({cand[0]} < {best[0]} - {tolerance}), not promoted =====")
            return

    # 4. PROVE: SPRT vs the live network.
    candidate = f"nnue:EvalFile={fwd(out + '.nnue')}"
    base = f"nnue:EvalFile={fwd(parent_net)}"
    run(["java", os.path.join("tools", "Sprt.java"), candidate, base, "--tc", args.tc, "--maxgames", "4000",
         "--concurrency", str(args.concurrency), "--label", f"v2 gen-{gen} vs gen-{parent}", "--jar", fwd(JAR)])
    with open(LEDGER, encoding="utf-8") as f:
        row = f.read().strip().splitlines()[-1]
    cells = [c.strip() for c in row.strip("|").split("|")]
    accepted = "H1" in row
    entry["tests"].append({"vs": f"gen-{parent}", "result": f"{cells[6]} W-L-D, Elo {cells[7]}, {cells[11]}", "note": ""})

    # 5. PROMOTE.
    if accepted:
        entry["promoted"] = True
        make_live(reg, gen, "promote")
        print(f"===== gen-{gen} PROMOTED: it is live; the Lichess bot uses it from its next game =====")
    else:
        save_registry(reg)
        print(f"===== gen-{gen} not better than gen-{parent}; gen-{parent} stays live =====")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--generations", type=int, default=1)
    ap.add_argument("--status", action="store_true")
    ap.add_argument("--rollback", type=int)
    ap.add_argument("--stop", action="store_true")
    ap.add_argument("--positions", type=int, default=3_000_000)
    ap.add_argument("--epochs", type=int, default=15)
    ap.add_argument("--tc", default="5+0.05")
    ap.add_argument("--threads", type=int, default=12)
    ap.add_argument("--concurrency", type=int, default=10)
    args = ap.parse_args()

    if args.status:
        return status()
    if args.rollback is not None:
        return rollback(args.rollback)
    if args.stop:
        return stop()

    if os.path.exists(PIDFILE):
        sys.exit("a generation is already running (tools\\train --stop to stop it)")
    os.makedirs(DATA, exist_ok=True)
    with open(PIDFILE, "w") as f:
        f.write(str(os.getpid()))
    try:
        built = os.path.join(V2, "target", "chess_engine_v2.jar")
        os.makedirs(os.path.dirname(JAR), exist_ok=True)
        shutil.copy(built, JAR)  # fixed engine build for the whole run
        for _ in range(args.generations):
            one_generation(args)
        print("\nDone. Start the next generation whenever you like: tools\\train")
    except KeyboardInterrupt:
        print("\nStopped (Ctrl+C). Nothing was promoted from the unfinished generation.")
    finally:
        if os.path.exists(PIDFILE):
            os.remove(PIDFILE)


if __name__ == "__main__":
    main()
