"""Trains an NNUE network on self-play (and Lichess) records and exports it for the Java engine.

  python chess_engine_v2/train/train.py --data tools/data/gen-0.bin --out chess_engine_v2/nets/gen-1
      [--epochs 15] [--lambda 0.75] [--init chess_engine_v2/nets/gen-0.pt] [--extra file.bin:0.15]

What it learns: for every position, predict
    target = lambda * sigmoid(search_score / 400) + (1 - lambda) * game_result
i.e. a blend of "what our search thought" and "what actually happened in the game". The game
result is the reinforcement signal: positions that looked fine but were lost teach the network
something the search alone could not.

Writes <out>.nnue (for the engine), <out>.pt (to continue training later) and <out>.golden.txt
(Java-vs-Python check), and prints train/validation loss per epoch.
"""

import argparse
import math
import os
import sys
import time

import numpy as np
import torch

sys.path.insert(0, os.path.dirname(__file__))
from data import RECORD, batch_arrays  # noqa: E402
from export import write_golden, write_nnue  # noqa: E402
from model import Nnue  # noqa: E402


def load_mix(main_paths, extras, rng):
    """Main data plus capped extras (e.g. Lichess games at most 15% of the mix)."""
    main = np.concatenate([np.fromfile(p, dtype=RECORD) for p in main_paths])
    parts = [main]
    for spec in extras:
        path, share = spec.rsplit(":", 1)
        extra = np.fromfile(path, dtype=RECORD)
        cap = int(len(main) * float(share) / (1 - float(share)))
        if len(extra) > cap:
            extra = extra[rng.choice(len(extra), cap, replace=False)]
        print(f"extra {path}: {len(extra):,} records")
        parts.append(extra)
    return np.concatenate(parts)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", nargs="+", required=True)
    ap.add_argument("--extra", nargs="*", default=[], help="path:share, e.g. lichess.bin:0.15")
    ap.add_argument("--out", required=True)
    ap.add_argument("--epochs", type=int, default=15)
    ap.add_argument("--batch", type=int, default=16384)
    ap.add_argument("--lr", type=float, default=1e-3)
    ap.add_argument("--lambda", dest="wdl", type=float, default=0.75)
    ap.add_argument("--init", help="start from this .pt checkpoint instead of random weights")
    ap.add_argument("--threads", type=int, default=os.cpu_count())
    ap.add_argument("--seed", type=int, default=1)
    args = ap.parse_args()

    torch.manual_seed(args.seed)
    torch.set_num_threads(args.threads)
    rng = np.random.default_rng(args.seed)

    records = load_mix(args.data, args.extra, rng)
    rng.shuffle(records)
    n_val = max(1000, len(records) // 50)
    val, train = records[:n_val], records[n_val:]
    print(f"{len(train):,} training / {len(val):,} validation positions")

    us, them, target, weight = (torch.from_numpy(a) for a in batch_arrays(train, args.wdl))
    v_us, v_them, v_target, v_weight = (torch.from_numpy(a) for a in batch_arrays(val, args.wdl))

    model = Nnue()
    if args.init:
        model.load_state_dict(torch.load(args.init))
        print(f"continuing from {args.init}")
    opt = torch.optim.Adam(model.parameters(), lr=args.lr)
    steps_per_epoch = math.ceil(len(train) / args.batch)
    sched = torch.optim.lr_scheduler.OneCycleLR(
        opt, max_lr=args.lr, total_steps=args.epochs * steps_per_epoch, pct_start=0.1
    )

    def loss_fn(u, t, tgt, w):
        pred = torch.sigmoid(model(u, t))
        return (w * (pred - tgt) ** 2).sum() / w.sum()

    for epoch in range(1, args.epochs + 1):
        start = time.time()
        model.train()
        perm = torch.randperm(len(train))
        total = 0.0
        for s in range(steps_per_epoch):
            idx = perm[s * args.batch : (s + 1) * args.batch]
            loss = loss_fn(us[idx], them[idx], target[idx], weight[idx])
            opt.zero_grad()
            loss.backward()
            opt.step()
            sched.step()
            model.clip_()
            total += loss.item()
        model.eval()
        with torch.no_grad():
            val_loss = loss_fn(v_us, v_them, v_target, v_weight).item()
        print(
            f"epoch {epoch:2d}/{args.epochs}  train {total / steps_per_epoch:.5f}"
            f"  val {val_loss:.5f}  ({time.time() - start:.0f}s)",
            flush=True,
        )

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    torch.save(model.state_dict(), args.out + ".pt")
    write_nnue(model, args.out + ".nnue")
    worst = write_golden(model, v_us.numpy(), v_them.numpy(), args.out + ".golden.txt")
    print(f"wrote {args.out}.nnue  (quantisation error on 200 positions: max {worst:.1f} cp)")


if __name__ == "__main__":
    main()
