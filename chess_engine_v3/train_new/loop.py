"""chess_engine_v3's self-improvement loop: one generation per run, proven before it is promoted.

  tools\\train_new                    one generation, then stop
  tools\\train_new --generations 3    several in a row
  tools\\train_new --status           every generation: parent, data, gates, anchor Elo, which is live
  tools\\train_new --rollback 2       make gen-2 the live v3 network again
  tools\\train_new --stop             stop a running generation (and everything it started)

One generation:
  1. PLAY     the live v3 network plays itself (C++ datagen, default 5M positions at 5000 nodes)
  2. ELITE    new Lichess-elite position files (tools/data/v3/elite/*.txt, from `engine_v3 pgn`) are
              labelled by our own search -> ~15% of the training mix
  3. LEARN    train on a rolling window of the newest data (default 40M positions), continuing from
              the parent's weights
  4. GATES    a. blunder suite (the bot's real Lichess mistakes, re-labelled by a deep v3 search):
                 the candidate must solve at least as many as the live network
              b. SPRT vs the live network
              c. ANCHOR: a fixed 200-game match vs a fixed opponent (v3 search + v2's gen-8 net),
                 recorded for every generation - the "real" cumulative Elo, immune to the
                 self-play inflation the v2 review found
  5. PROMOTE  only if gates a and b pass: chess_engine_v3/nets/best.nnue is replaced atomically

The Lichess bot is NOT touched: it keeps playing v2 until you switch it yourself.
Generation 1 (the first run) trains from scratch on all existing v2 data.
"""

import argparse
import datetime
import glob
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
V3 = os.path.join(ROOT, "chess_engine_v3")
TRAIN = os.path.join(V3, "train_new")
NETS = os.path.join(V3, "nets")
DATA = os.path.join(ROOT, "tools", "data")
V3DATA = os.path.join(DATA, "v3")
ELITE = os.path.join(V3DATA, "elite")
REGISTRY = os.path.join(NETS, "registry.json")
BEST = os.path.join(NETS, "best.nnue")
EXE_BUILT = os.path.join(V3, "bin", "engine_v3.exe")
EXE = os.path.join(ROOT, "tools", "versions", "loop-engine-v3.exe")
ANCHOR_NET = os.path.join(ROOT, "chess_engine_v2", "nets", "gen-8.nnue")
PIDFILE = os.path.join(V3DATA, "train.pid")
GENERATIONS_MD = os.path.join(ROOT, "docs", "results", "v3-generations.md")
LEDGER = os.path.join(ROOT, "docs", "results", "elo-ledger.md")
BLUNDERS = os.path.join(V3, "tests", "data", "lichess-blunders-v3.epd")

# Below-normal priority: training never slows down the Lichess bot (children inherit it).
LOW_PRIORITY = getattr(subprocess, "BELOW_NORMAL_PRIORITY_CLASS", 0)


def now():
    return datetime.datetime.now().isoformat(timespec="seconds")


def fwd(path):
    return path.replace("\\", "/")


# ---------------------------------------------------------------- registry

def load_registry():
    if not os.path.exists(REGISTRY):
        return {"best": None, "generations": [], "events": []}
    with open(REGISTRY, encoding="utf-8") as f:
        return json.load(f)


def save_registry(reg):
    os.makedirs(NETS, exist_ok=True)
    tmp = REGISTRY + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(reg, f, indent=2)
    os.replace(tmp, REGISTRY)
    write_generations_md(reg)


def gen_entry(reg, n):
    return next((g for g in reg["generations"] if g["gen"] == n), None)


def next_number(reg):
    used = [g["gen"] for g in reg["generations"]]
    for name in os.listdir(NETS) if os.path.isdir(NETS) else []:
        if name.startswith("gen-") and name.endswith(".nnue"):
            used.append(int(name[4:-5]))
    return max(used, default=0) + 1


def make_live(reg, n, event):
    """Atomically replace chess_engine_v3/nets/best.nnue with gen-n."""
    shutil.copy(os.path.join(NETS, f"gen-{n}.nnue"), BEST + ".tmp")
    os.replace(BEST + ".tmp", BEST)
    with open(os.path.join(NETS, "best.txt"), "w") as f:
        f.write(str(n))
    reg["best"] = n
    reg["events"].append({"time": now(), "event": event, "gen": n})
    save_registry(reg)


def write_generations_md(reg):
    lines = [
        "# chess_engine_v3 generations",
        "",
        "Generated from `chess_engine_v3/nets/registry.json` by `chess_engine_v3/train_new/loop.py`.",
        "A generation becomes the live v3 network (`chess_engine_v3/nets/best.nnue`) only after the",
        "blunder gate and SPRT H1 vs the current one. **Anchor** = a fixed 200-game match against the",
        "same opponent every time (v3 search + v2's gen-8 net), so its Elo column is directly comparable",
        "across generations. The Lichess bot is switched to v3 only by hand.",
        "",
        f"**Live v3 network: {'gen-' + str(reg['best']) if reg['best'] else 'none yet'}**",
        "",
        "| Gen | Parent | Shape | Positions | Blunders (live) | SPRT vs live | Anchor Elo | Promoted |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for g in reg["generations"]:
        lines.append(
            f"| {g['gen']} | {g.get('parent') or '—'} | {g.get('shape', '—')} | {g.get('positions', 0):,} "
            f"| {g.get('blunders', '—')} | {g.get('sprt', '—')} | {g.get('anchor', '—')} "
            f"| {'✅' if g.get('promoted') else '❌'} |"
        )
    lines += ["", "## Events", "", "| Time | Event | Gen |", "|---|---|---|"]
    for e in reg["events"]:
        lines.append(f"| {e['time']} | {e['event']} | {e['gen']} |")
    os.makedirs(os.path.dirname(GENERATIONS_MD), exist_ok=True)
    with open(GENERATIONS_MD, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


# ---------------------------------------------------------------- helpers

def run(cmd, capture=False):
    print("\n$ " + " ".join(cmd), flush=True)
    if capture:
        p = subprocess.run(cmd, cwd=ROOT, creationflags=LOW_PRIORITY, capture_output=True, text=True)
        sys.stdout.write(p.stderr)
        return p.returncode, p.stdout
    return subprocess.run(cmd, cwd=ROOT, creationflags=LOW_PRIORITY).returncode, ""


def blunder_score(net, threads):
    if not os.path.exists(BLUNDERS):
        return None
    cmd = [EXE, "blunders", "--epd", BLUNDERS, "--threads", str(threads)]
    if net:
        cmd += ["--net", net]
    code, out = run(cmd, capture=True)
    rows = [l for l in out.splitlines() if l.startswith("SOLVED")]
    if code != 0 or not rows:
        return None
    _, solved, total = rows[-1].split()
    return int(solved), int(total)


def last_ledger_row():
    with open(LEDGER, encoding="utf-8") as f:
        row = f.read().strip().splitlines()[-1]
    return row, [c.strip() for c in row.strip("|").split("|")]


def sprt(candidate_net, base_net, args, label, games=0):
    """Runs tools/Sprt.java with the loop's engine snapshot; returns (H1 accepted, summary)."""
    cand = f"v3:EvalFile={fwd(candidate_net)}"
    base = f"v3:EvalFile={fwd(base_net)}"
    cmd = ["java", os.path.join("tools", "Sprt.java"), cand, base, "--tc", args.tc,
           "--concurrency", str(args.concurrency), "--label", label, "--jar", fwd(EXE)]
    cmd += ["--games", str(games)] if games else ["--maxgames", "4000"]
    run(cmd)
    row, cells = last_ledger_row()
    return "H1" in row, f"{cells[6]} W-L-D, Elo {cells[7]}"


def training_data(reg, window):
    """Newest v3 self-play sets first, then v2's sets, up to `window` positions."""
    v3_sets = sorted(glob.glob(os.path.join(V3DATA, "selfplay-*.bin")), key=os.path.getmtime, reverse=True)
    v2_sets = sorted(glob.glob(os.path.join(DATA, "selfplay-gen*.bin")) + glob.glob(os.path.join(DATA, "gen-[0-9].bin")),
                     key=os.path.getmtime, reverse=True)
    chosen, total = [], 0
    for path in v3_sets + v2_sets:
        if total >= window:
            break
        chosen.append(path)
        total += os.path.getsize(path) // 72
    return chosen, total


def label_new_elite(net, threads):
    """Labels every elite position file that has no .bin yet; returns all elite .bin files."""
    os.makedirs(ELITE, exist_ok=True)
    for txt in sorted(glob.glob(os.path.join(ELITE, "*.txt"))):
        out = txt[:-4] + ".bin"
        if not os.path.exists(out):
            cmd = [EXE, "label", "--in", txt, "--out", out, "--nodes", "10000", "--threads", str(threads)]
            if net:
                cmd += ["--net", net]
            run(cmd)
    return sorted(glob.glob(os.path.join(ELITE, "*.bin")))


# ---------------------------------------------------------------- one generation

def one_generation(args):
    reg = load_registry()
    parent = reg["best"]
    parent_net = os.path.join(NETS, f"gen-{parent}.nnue") if parent else None
    gen = next_number(reg)
    print(f"\n===== chess_engine_v3: training gen-{gen} (parent: {'gen-' + str(parent) if parent else 'none, from scratch'}) =====",
          flush=True)
    os.makedirs(V3DATA, exist_ok=True)

    # 1. PLAY (skipped for gen-1: it learns from all of v2's existing data first).
    if parent:
        data = os.path.join(V3DATA, f"selfplay-gen{parent}-for-gen{gen}.bin")
        if not os.path.exists(data):
            code, _ = run([EXE, "datagen", "--net", parent_net, "--positions", str(args.positions),
                           "--nodes", str(args.nodes), "--out", data, "--threads", str(args.threads),
                           "--seed", str(5000 + gen)])
            if code != 0:
                sys.exit("self-play failed")

    # 2. ELITE + the bot's own games (both labelled by our search).
    extras = []
    elite = label_new_elite(parent_net, args.threads)
    if elite:
        share = 0.15 / len(elite)
        extras += [f"{p}:{share:.4f}" for p in elite]
    lichess_bin = os.path.join(DATA, "lichess.bin")
    if os.path.exists(lichess_bin) and os.path.getsize(lichess_bin) > 0:
        extras.append(lichess_bin + ":0.05")

    # 3. LEARN.
    datasets, positions = training_data(reg, args.window)
    out = os.path.join(NETS, f"gen-{gen}")
    shape = f"(768x{args.king_buckets} -> {args.hidden})x2 -> {args.output_buckets}"
    cmd = [sys.executable, os.path.join(TRAIN, "train.py"), "--data", *datasets, "--out", out,
           "--epochs", str(args.epochs), "--threads", str(args.threads), "--hidden", str(args.hidden),
           "--king-buckets", str(args.king_buckets), "--output-buckets", str(args.output_buckets)]
    if extras:
        cmd += ["--extra", *extras]
    parent_entry = gen_entry(reg, parent) if parent else None
    if parent and parent_entry and parent_entry.get("shape") == shape and os.path.exists(os.path.join(NETS, f"gen-{parent}.pt")):
        cmd += ["--init", os.path.join(NETS, f"gen-{parent}.pt")]
    if run(cmd)[0] != 0:
        sys.exit("training failed")

    entry = {"gen": gen, "parent": parent, "date": now(), "shape": shape,
             "data": [os.path.basename(d) for d in datasets] + [os.path.basename(e.rsplit(":", 1)[0]) for e in extras],
             "positions": positions, "promoted": False}
    reg["generations"].append(entry)
    save_registry(reg)

    # 4a. GATE: the bot's real blunders (live = parent, or v2's gen-8 for the first v3 network).
    reference = parent_net or ANCHOR_NET
    cand, live = blunder_score(out + ".nnue", args.threads), blunder_score(reference, args.threads)
    if cand and live:
        entry["blunders"] = f"{cand[0]}/{cand[1]} ({live[0]})"
        save_registry(reg)
        if cand[0] < live[0] - args.blunder_tolerance:
            entry["sprt"] = "not run (blunder gate)"
            save_registry(reg)
            print(f"===== gen-{gen}: failed the blunder gate ({cand[0]} < {live[0]}), not promoted =====")
            return

    # 4b. SPRT vs the live network.
    accepted, summary = sprt(out + ".nnue", reference, args,
                             f"v3 gen-{gen} vs {'gen-' + str(parent) if parent else 'v2 gen-8 net'} (v3 search)")
    entry["sprt"] = summary + (" ✅ H1" if accepted else " ❌")
    save_registry(reg)

    # 4c. ANCHOR: fixed match vs the fixed opponent, every generation.
    if args.anchor_games:
        _, anchor = sprt(out + ".nnue", ANCHOR_NET, args, f"v3 gen-{gen} anchor match vs v2 gen-8 net (v3 search)",
                         games=args.anchor_games)
        entry["anchor"] = anchor
        save_registry(reg)

    # 5. PROMOTE.
    if accepted:
        entry["promoted"] = True
        make_live(reg, gen, "promote")
        print(f"===== gen-{gen} PROMOTED: chess_engine_v3/nets/best.nnue (the Lichess bot is unchanged) =====")
    else:
        print(f"===== gen-{gen} not better; {'gen-' + str(parent) if parent else 'no v3 network'} stays live =====")


# ---------------------------------------------------------------- commands

def status():
    reg = load_registry()
    print(f"Live v3 network: {'gen-' + str(reg['best']) if reg['best'] else 'none yet'}\n")
    for g in reg["generations"]:
        print(f"gen-{g['gen']:<3} parent={g.get('parent')!s:<5} {'PROMOTED' if g.get('promoted') else 'rejected':<9}"
              f" blunders={g.get('blunders', '-')}  sprt={g.get('sprt', '-')}  anchor={g.get('anchor', '-')}")
    gains = [g for g in reg["generations"] if g.get("promoted")]
    if len(gains) >= 2:
        print("\nStop rule: two generations in a row under +15 Elo -> grow the network or the data instead.")


def rollback(n):
    reg = load_registry()
    if not os.path.exists(os.path.join(NETS, f"gen-{n}.nnue")):
        sys.exit(f"gen-{n}.nnue does not exist")
    make_live(reg, n, "rollback")
    print(f"v3 gen-{n} is the live v3 network again.")


def stop():
    if not os.path.exists(PIDFILE):
        print("no generation is running")
        return
    pid = open(PIDFILE).read().strip()
    subprocess.run(["taskkill", "/PID", pid, "/T", "/F"], capture_output=True)
    os.remove(PIDFILE)
    print(f"stopped the running generation (process tree {pid})")


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--generations", type=int, default=1)
    ap.add_argument("--status", action="store_true")
    ap.add_argument("--rollback", type=int)
    ap.add_argument("--stop", action="store_true")
    ap.add_argument("--positions", type=int, default=5_000_000)
    ap.add_argument("--nodes", type=int, default=5000)
    ap.add_argument("--window", type=int, default=40_000_000, help="positions of newest data to train on")
    ap.add_argument("--epochs", type=int, default=12)
    ap.add_argument("--hidden", type=int, default=768)
    ap.add_argument("--king-buckets", type=int, default=4)
    ap.add_argument("--output-buckets", type=int, default=8)
    ap.add_argument("--tc", default="8+0.08")
    ap.add_argument("--threads", type=int, default=12)
    ap.add_argument("--concurrency", type=int, default=8)
    ap.add_argument("--anchor-games", type=int, default=200)
    ap.add_argument("--blunder-tolerance", type=int, default=0)
    ap.add_argument("--sandbox", help="dry run: keep nets, registry and new data in this folder")
    args = ap.parse_args()

    if args.sandbox:
        global NETS, V3DATA, ELITE, REGISTRY, BEST, PIDFILE, GENERATIONS_MD
        box = os.path.abspath(args.sandbox)
        NETS, V3DATA = os.path.join(box, "nets"), os.path.join(box, "data")
        ELITE = os.path.join(V3DATA, "elite")
        REGISTRY, BEST = os.path.join(NETS, "registry.json"), os.path.join(NETS, "best.nnue")
        PIDFILE, GENERATIONS_MD = os.path.join(V3DATA, "train.pid"), os.path.join(box, "v3-generations.md")
        os.makedirs(NETS, exist_ok=True)
        print(f"sandbox: {box}")

    if args.status:
        return status()
    if args.rollback is not None:
        return rollback(args.rollback)
    if args.stop:
        return stop()

    os.makedirs(V3DATA, exist_ok=True)
    if os.path.exists(PIDFILE):
        sys.exit("a generation is already running (tools\\train_new --stop to stop it)")
    if not os.path.exists(EXE_BUILT):
        sys.exit("build the engine first: tools\\engine3 build")
    with open(PIDFILE, "w") as f:
        f.write(str(os.getpid()))
    try:
        os.makedirs(os.path.dirname(EXE), exist_ok=True)
        shutil.copy(EXE_BUILT, EXE)  # fixed engine build for the whole run
        for _ in range(args.generations):
            one_generation(args)
        print("\nDone. Start the next generation whenever you like: tools\\train_new")
    except KeyboardInterrupt:
        print("\nStopped (Ctrl+C). Nothing was promoted from the unfinished generation.")
    finally:
        if os.path.exists(PIDFILE):
            os.remove(PIDFILE)


if __name__ == "__main__":
    main()
