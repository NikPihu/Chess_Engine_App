"""Plots for docs/results/generation-review.md.

Reads chess_engine_v2/nets/registry.json (SPRT results, blunder suite, Lichess ratings at each
promotion) and the per-generation Lichess table, and writes PNGs to docs/results/img/.

    python tools/plot_generations.py
"""
import json
import math
import re
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
REG = ROOT / "chess_engine_v2/nets/registry.json"
LICHESS_MD = ROOT / "docs/results/lichess-by-generation.md"
OUT = ROOT / "docs/results/img"

BLUE, ORANGE, GREEN, RED, GREY = "#2a6fdb", "#e8862a", "#2e9e5b", "#d64545", "#8a8f98"


def sprt_rows(reg):
    """One row per promoted generation: its valid SPRT vs the parent and its blunder-suite line."""
    rows = []
    for g in reg["generations"]:
        if not g.get("promoted"):
            continue
        sprt = None
        for t in g["tests"]:
            m = re.search(r"(\d+)-(\d+)-(\d+) W-L-D, Elo ([+-]?[\d.]+)(?: \+/- ([\d.]+))?", t["result"])
            if m and "H1" in t["result"]:
                w, l, d = map(int, m.group(1, 2, 3))
                elo = float(m.group(4))
                n = w + l + d
                err = float(m.group(5)) if m.group(5) else elo_error(w, l, d)
                sprt = dict(w=w, l=l, d=d, n=n, elo=elo, err=err)
        bs = None
        for t in g["tests"]:
            m = re.search(r"(\d+)/(\d+) \(gen-\d+: (\d+)", str(t.get("blunder", "")) + " " + t["result"])
            if m:
                bs = tuple(map(int, m.groups()))
        if bs is None:
            m = re.search(r"(\d+)/(\d+) \(gen-\d+: (\d+)", json.dumps(g))
            bs = tuple(map(int, m.groups())) if m else None
        rows.append(dict(gen=g["gen"], sprt=sprt, blunder=bs))
    return rows


def elo_error(w, l, d):
    """95% interval half-width in Elo, from the per-game score variance."""
    n = w + l + d
    s = (w + 0.5 * d) / n
    var = (w * (1 - s) ** 2 + l * s ** 2 + d * (0.5 - s) ** 2) / n
    se = math.sqrt(var / n)
    lo, hi = s - 1.96 * se, s + 1.96 * se
    f = lambda x: -400 * math.log10(1 / x - 1)  # noqa: E731
    return (f(hi) - f(lo)) / 2


def lichess_table():
    rows = []
    for line in LICHESS_MD.read_text(encoding="utf-8").splitlines():
        m = re.match(r"\| (v1|gen-\d+) \| (\d+) \| (\d+) \| (\d+) \| (\d+) \|", line)
        if m:
            rows.append(dict(engine=m.group(1), n=int(m.group(2)), w=int(m.group(3)),
                             d=int(m.group(4)), l=int(m.group(5))))
    return rows


def style(ax, title, xlabel, ylabel):
    ax.set_title(title, fontsize=12, loc="left", fontweight="bold")
    ax.set_xlabel(xlabel)
    ax.set_ylabel(ylabel)
    ax.grid(axis="y", alpha=0.3)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    reg = json.loads(REG.read_text(encoding="utf-8"))
    rows = [r for r in sprt_rows(reg) if r["sprt"]]
    gens = [r["gen"] for r in rows]
    labels = [f"gen-{g}" for g in gens]
    elo = [r["sprt"]["elo"] for r in rows]
    err = [r["sprt"]["err"] for r in rows]

    # 1. Per-generation SPRT gain vs its parent.
    fig, ax = plt.subplots(figsize=(8, 4.2))
    ax.bar(labels, elo, color=BLUE, yerr=err, capsize=5, error_kw=dict(ecolor=GREY, lw=1.2))
    for i, (e, x) in enumerate(zip(elo, err)):
        ax.text(i, e + x + 3, f"+{e:.0f}", ha="center", fontsize=10)
    ax.axhline(15, color=RED, ls="--", lw=1)
    ax.text(-0.45, 17, "stop line ~15 Elo", color=RED, ha="left", fontsize=9)
    style(ax, "Elo gained per generation (SPRT vs its parent, 95% bars)",
          "candidate (gen-2 is vs v1)", "Elo vs parent")
    fig.tight_layout()
    fig.savefig(OUT / "gen-sprt-gain.png", dpi=140)
    plt.close(fig)

    # 2. Cumulative self-play Elo vs Lichess ratings at each promotion.
    cum, total = [], 0.0
    for e in elo:
        total += e
        cum.append(total)
    ev = {e["gen"]: e["lichess"] for e in reg["events"] if e["lichess"]}
    # rating at the moment gen-N went live = the snapshot at its promote (gen-2: the deploy).
    bullet = [ev.get(g, {}).get("bullet") for g in gens]
    blitz = [ev.get(g, {}).get("blitz") for g in gens]
    fig, ax = plt.subplots(figsize=(8, 4.4))
    ax.plot(labels, [c - cum[0] for c in cum], "-o", color=BLUE, lw=2,
            label="self-play Elo (sum of SPRT gains, from gen-2)")
    ax.plot(labels, [b - bullet[0] for b in bullet], "-s", color=ORANGE, lw=2,
            label=f"Lichess bullet (from {bullet[0]})")
    ax.plot(labels, [b - blitz[0] for b in blitz], "-^", color=GREEN, lw=2,
            label=f"Lichess blitz (from {blitz[0]})")
    ax.annotate(f"+{cum[-1] - cum[0]:.0f}", (len(labels) - 1, cum[-1] - cum[0]),
                textcoords="offset points", xytext=(6, -4), color=BLUE)
    ax.annotate(f"+{bullet[-1] - bullet[0]}", (len(labels) - 1, bullet[-1] - bullet[0]),
                textcoords="offset points", xytext=(6, 4), color=ORANGE)
    ax.annotate(f"+{blitz[-1] - blitz[0]}", (len(labels) - 1, blitz[-1] - blitz[0]),
                textcoords="offset points", xytext=(6, -12), color=GREEN)
    ax.legend(frameon=False, fontsize=9, loc="upper left")
    style(ax, "Self-play says +313, Lichess says +30 to +100",
          "generation going live", "Elo / rating change since gen-2")
    fig.tight_layout()
    fig.savefig(OUT / "gen-selfplay-vs-lichess.png", dpi=140)
    plt.close(fig)

    # 3. Blunder suite: candidate vs parent on the same suite.
    br = [r for r in rows if r["blunder"]]
    bl = [f"gen-{r['gen']}" for r in br]
    cand = [100 * r["blunder"][0] / r["blunder"][1] for r in br]
    par = [100 * r["blunder"][2] / r["blunder"][1] for r in br]
    fig, ax = plt.subplots(figsize=(8, 4.2))
    x = range(len(bl))
    ax.bar([i - 0.2 for i in x], par, 0.4, color=GREY, label="parent")
    ax.bar([i + 0.2 for i in x], cand, 0.4, color=BLUE, label="candidate")
    for i, r in enumerate(br):
        diff = r["blunder"][0] - r["blunder"][2]
        ax.text(i, max(cand[i], par[i]) + 1, f"{diff:+d}", ha="center",
                color=GREEN if diff > 0 else RED, fontweight="bold")
        ax.text(i, 3, f"n={r['blunder'][1]}", ha="center", color="white", fontsize=8)
    ax.set_xticks(list(x), bl)
    ax.set_ylim(0, 70)
    ax.legend(frameon=False, fontsize=9, loc="upper right")
    style(ax, "Blunder suite (the bot's real Lichess mistakes): no progress",
          "candidate generation", "% of positions solved")
    fig.tight_layout()
    fig.savefig(OUT / "gen-blunder-suite.png", dpi=140)
    plt.close(fig)

    # 4. Lichess score per generation with a 95% interval, games count on top.
    lt = lichess_table()
    fig, ax = plt.subplots(figsize=(8, 4.2))
    names = [r["engine"] for r in lt]
    score = [100 * (r["w"] + 0.5 * r["d"]) / r["n"] for r in lt]
    ci = [196 * math.sqrt(max(s / 100 * (1 - s / 100), 0.05) / r["n"]) for s, r in zip(score, lt)]
    colors = [GREY if n == "v1" else BLUE for n in names]
    ax.bar(names, score, color=colors, yerr=ci, capsize=5, error_kw=dict(ecolor=GREY, lw=1.2))
    for i, r in enumerate(lt):
        ax.text(i, 4, f"{r['n']} g", ha="center", color="white", fontsize=9)
    ax.axhline(50, color="black", lw=0.8)
    ax.set_ylim(0, 105)
    style(ax, "Lichess score per live generation (95% bars: samples are tiny)",
          "network live when the game started", "score %")
    fig.tight_layout()
    fig.savefig(OUT / "gen-lichess-score.png", dpi=140)
    plt.close(fig)

    # 5. SPRT cost and draw rate.
    n = [r["sprt"]["n"] for r in rows]
    draw = [100 * r["sprt"]["d"] / r["sprt"]["n"] for r in rows]
    fig, ax = plt.subplots(figsize=(8, 4.2))
    ax.bar(labels, n, color=GREY, alpha=0.6, label="SPRT games needed")
    ax2 = ax.twinx()
    ax2.plot(labels, draw, "-o", color=ORANGE, lw=2, label="draw rate %")
    ax2.set_ylabel("draw rate %", color=ORANGE)
    ax2.set_ylim(0, 50)
    style(ax, "Matches get longer and drawier as the nets converge", "candidate", "games")
    ax2.spines["top"].set_visible(False)
    h1, l1 = ax.get_legend_handles_labels()
    h2, l2 = ax2.get_legend_handles_labels()
    ax.legend(h1 + h2, l1 + l2, frameon=False, fontsize=9, loc="upper left")
    fig.tight_layout()
    fig.savefig(OUT / "gen-sprt-cost.png", dpi=140)
    plt.close(fig)

    for r in rows:
        s = r["sprt"]
        print(f"gen-{r['gen']}: {s['w']}-{s['l']}-{s['d']} n={s['n']} elo={s['elo']:+.1f}±{s['err']:.1f} "
              f"draw={100 * s['d'] / s['n']:.1f}% blunder={r['blunder']}")
    print("cumulative:", [round(c, 1) for c in cum])
    print("wrote", sorted(p.name for p in OUT.glob("gen-*.png")))


if __name__ == "__main__":
    main()
