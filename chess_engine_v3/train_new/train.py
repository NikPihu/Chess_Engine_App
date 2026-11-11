"""Trains a v3 network (king buckets + output heads) on 72-byte training records.

  python chess_engine_v3/train_new/train.py --data tools/data/selfplay-*.bin --extra tools/data/lichess.bin:0.15
      --out chess_engine_v3/nets/v3net-1  [--hidden 768] [--king-buckets 4] [--output-buckets 8]
      [--epochs 20] [--batch 16384] [--lr 1e-3] [--lambda 0.75] [--init file.pt] [--threads N]

What it learns, as in v2:
    target = lambda * sigmoid(search_score / 400) + (1 - lambda) * game_result
The search score teaches "what our search thought"; the game result teaches what actually happened.

Difference from v2's trainer: data is *streamed*. v2 built every batch's tensors up front (about
512 bytes per position in RAM: 50M positions would need 25 GB). Here the records stay in memory-
mapped files (72 bytes each, paged in by the OS) and each batch is converted on the fly, so the
data set can be as big as the disk.

Writes <out>.nnue (engine), <out>.pt (to continue training) and <out>.golden.txt (C++ check).

Validation (fixed after v3 gen-3 memorised its data): the last --holdout (2%) of every big file
is a set of whole games that is never trained on. The held-out loss is printed next to the loss on
already-seen positions; training stops once the held-out loss has not improved for --patience
epochs, and the best epoch (not the last) is exported. --max-repeat caps how often a small --extra
set can be reused per epoch.

Crash-safe: after every epoch the full training state (weights, optimizer, learning-rate schedule,
random generator) is saved to <out>.ckpt. Run the same command again with --resume and training
continues with the next epoch. <out>.nnue is only written at the very end, so its existence means
"finished".
"""

import argparse
import glob
import math
import os
import sys
import time

import numpy as np
import torch

sys.path.insert(0, os.path.dirname(__file__))
from export import write_golden, write_nnue  # noqa: E402
from features import Layout, batch_inputs  # noqa: E402
from model import NnueV3  # noqa: E402

RECORD = np.dtype(
    [("features", "<u2", (32,)), ("score", "<i2"), ("stm", "u1"), ("result", "u1"),
     ("weight", "u1"), ("reserved", "u1", (3,))]
)
assert RECORD.itemsize == 72


HOLDOUT_MIN_RECORDS = 50_000  # files smaller than this give no held-out games


class Dataset:
    """Several memory-mapped record files seen as one array, with per-file sampling shares.

    The last `holdout` fraction of every big file is never trained on. Self-play writes each game
    in one piece, so a file's tail is a set of whole games the network has never seen - an honest
    validation set (a random sample would share games, and so near-identical positions, with the
    training data and could not detect memorisation).
    """

    def __init__(self, main_paths, extras, rng, holdout=0.02, max_repeat=2.0):
        self.parts = []  # (memmap, samples per epoch, trainable rows = the file minus its held-out tail)
        main = [np.memmap(p, dtype=RECORD, mode="r") for p in main_paths]

        def trainable(m):
            return len(m) - (int(len(m) * holdout) if len(m) >= HOLDOUT_MIN_RECORDS else 0)

        main_total = sum(trainable(m) for m in main)
        for m in main:
            self.parts.append((m, trainable(m), trainable(m)))
        for spec in extras:
            path, share = spec.rsplit(":", 1)
            share = float(share)
            m = np.memmap(path, dtype=RECORD, mode="r")
            # The extra set gets `share` of the final mix (sampled with replacement if small), but
            # no position is drawn more than `max_repeat` times per epoch on average: a tiny set
            # asked for a big share would otherwise just be memorised.
            want = int(main_total * share / (1 - share))
            cap = int(trainable(m) * max_repeat)
            note = ""
            if want > cap:
                want, note = cap, f" (capped at {max_repeat:g}x its size)"
            self.parts.append((m, want, trainable(m)))
            print(f"extra {path}: {len(m):,} records, {want:,} samples per epoch{note}")
        self.rng = rng
        self.size = sum(n for _, n, _ in self.parts)

    def epoch_index(self):
        """(part, row) pairs for one shuffled epoch (held-out tails excluded)."""
        part_ids, rows = [], []
        for i, (m, n, usable) in enumerate(self.parts):
            r = self.rng.permutation(usable)[:n] if n <= usable else self.rng.integers(0, usable, n)
            part_ids.append(np.full(len(r), i, dtype=np.int8))
            rows.append(r)
        part_ids = np.concatenate(part_ids)
        rows = np.concatenate(rows)
        order = self.rng.permutation(len(rows))
        return part_ids[order], rows[order]

    def holdout_index(self, count, rng):
        """(part, row) pairs from the held-out tails, each file weighted by its share of an epoch."""
        part_ids, rows = [], []
        held = [(i, m, n, usable) for i, (m, n, usable) in enumerate(self.parts) if usable < len(m)]
        weight = sum(n for _, _, n, _ in held)
        for i, m, n, usable in held:
            k = min(len(m) - usable, max(1, round(count * n / weight)))
            r = usable + rng.choice(len(m) - usable, k, replace=False)
            part_ids.append(np.full(k, i, dtype=np.int8))
            rows.append(r)
        if not rows:
            return np.empty(0, np.int8), np.empty(0, np.int64)
        return np.concatenate(part_ids), np.concatenate(rows)

    def gather(self, part_ids, rows):
        out = np.empty(len(rows), dtype=RECORD)
        for i, (m, _, _) in enumerate(self.parts):
            sel = part_ids == i
            if sel.any():
                idx = rows[sel]
                order = np.argsort(idx)  # sorted reads are much faster on a memmap
                out_sel = np.empty(len(idx), dtype=RECORD)
                out_sel[order] = m[idx[order]]
                out[sel] = out_sel
        return out


def tensors(layout, records, wdl):
    us, them, bucket = batch_inputs(layout, records)
    stm_black = records["stm"] == 1
    score = records["score"].astype(np.float32) * np.where(stm_black, -1.0, 1.0)
    result = records["result"].astype(np.float32) / 2.0
    result = np.where(stm_black, 1.0 - result, result)
    target = wdl / (1.0 + np.exp(-score / 400.0)) + (1.0 - wdl) * result
    weight = records["weight"].astype(np.float32)
    return (torch.from_numpy(us), torch.from_numpy(them), torch.from_numpy(bucket),
            torch.from_numpy(target.astype(np.float32)), torch.from_numpy(weight))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", nargs="+", required=True, help="record files (globs allowed)")
    ap.add_argument("--extra", nargs="*", default=[], help="path:share, e.g. lichess.bin:0.15")
    ap.add_argument("--out", required=True)
    ap.add_argument("--hidden", type=int, default=768)
    ap.add_argument("--king-buckets", type=int, default=4)
    ap.add_argument("--output-buckets", type=int, default=8)
    ap.add_argument("--no-mirror", action="store_true")
    ap.add_argument("--epochs", type=int, default=20)
    ap.add_argument("--batch", type=int, default=16384)
    ap.add_argument("--lr", type=float, default=1e-3)
    ap.add_argument("--lambda", dest="wdl", type=float, default=0.75)
    ap.add_argument("--init", help="continue from this .pt checkpoint (same shape)")
    ap.add_argument("--threads", type=int, default=max(1, (os.cpu_count() or 4) - 4))
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--max-steps", type=int, default=0, help="stop early (smoke tests)")
    ap.add_argument("--resume", action="store_true", help="continue from <out>.ckpt if it exists")
    ap.add_argument("--holdout", type=float, default=0.02,
                    help="fraction at the end of every big file (whole games) kept out of training for validation")
    ap.add_argument("--patience", type=int, default=2,
                    help="stop after this many epochs without a better held-out loss (0 = never stop early)")
    ap.add_argument("--max-repeat", type=float, default=2.0,
                    help="an --extra set is drawn at most this many times its size per epoch")
    args = ap.parse_args()

    torch.manual_seed(args.seed)
    torch.set_num_threads(args.threads)
    rng = np.random.default_rng(args.seed)
    layout = Layout(args.king_buckets, args.output_buckets, not args.no_mirror)

    paths = sorted({p for pattern in args.data for p in glob.glob(pattern)})
    if not paths:
        sys.exit("no data files match " + " ".join(args.data))
    print("data:", *paths, sep="\n  ")
    data = Dataset(paths, args.extra, rng, args.holdout, args.max_repeat)

    # A fixed validation sample from the held-out games (never trained on), and for comparison a
    # sample of training positions: a growing gap between the two means the net is memorising.
    vp, vr = data.holdout_index(100_000, np.random.default_rng(args.seed + 1))
    if len(vr) == 0:
        sys.exit("no held-out data: use --holdout > 0 and at least one file of 50,000+ records")
    val = data.gather(vp, vr)
    v_us, v_them, v_bucket, v_target, v_weight = tensors(layout, val, args.wdl)
    tp, tr = data.epoch_index()
    seen = data.gather(tp[:len(vr)], tr[:len(vr)])
    s_us, s_them, s_bucket, s_target, s_weight = tensors(layout, seen, args.wdl)
    rng = np.random.default_rng(args.seed)  # the epoch_index above must not shift training's shuffles
    data.rng = rng
    print(f"{data.size:,} samples per epoch, {len(vr):,} held-out validation positions (unseen games)")

    model = NnueV3(layout.inputs, args.hidden, args.output_buckets)
    if args.init:
        model.load_state_dict(torch.load(args.init))
        print(f"continuing from {args.init}")
    opt = torch.optim.Adam(model.parameters(), lr=args.lr)
    steps_per_epoch = math.ceil(data.size / args.batch)
    total_steps = args.epochs * steps_per_epoch
    if args.max_steps:
        total_steps = min(total_steps, args.max_steps)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=args.lr, total_steps=total_steps, pct_start=0.05)

    def loss_fn(u, t, b, tgt, w):
        pred = torch.sigmoid(model(u, t, b))
        return (w * (pred - tgt) ** 2).sum() / w.sum()

    step = 0
    first_epoch = 1
    best_val, best_epoch = float("inf"), 0
    ckpt_path = args.out + ".ckpt"
    if args.resume and os.path.exists(ckpt_path):
        ckpt = torch.load(ckpt_path, weights_only=False)
        model.load_state_dict(ckpt["model"])
        opt.load_state_dict(ckpt["opt"])
        sched.load_state_dict(ckpt["sched"])
        rng.bit_generator.state = ckpt["rng"]
        step = ckpt["step"]
        first_epoch = ckpt["epoch"] + 1
        best_val, best_epoch = ckpt.get("best_val", float("inf")), ckpt.get("best_epoch", 0)
        print(f"resuming after epoch {ckpt['epoch']} (step {step})", flush=True)
    print(f"model {layout.king_buckets}x768 -> {args.hidden} x2 -> {args.output_buckets} heads, "
          f"{sum(p.numel() for p in model.parameters()):,} parameters, {args.threads} threads", flush=True)
    for epoch in range(first_epoch, args.epochs + 1):
        start = time.time()
        model.train()
        parts, rows = data.epoch_index()
        total = 0.0
        n = 0
        for s in range(steps_per_epoch):
            if step >= total_steps:
                break
            batch = data.gather(parts[s * args.batch:(s + 1) * args.batch], rows[s * args.batch:(s + 1) * args.batch])
            u, t, b, tgt, w = tensors(layout, batch, args.wdl)
            loss = loss_fn(u, t, b, tgt, w)
            opt.zero_grad()
            loss.backward()
            opt.step()
            sched.step()
            model.clip_()
            total += loss.item()
            n += 1
            step += 1
            if s % 200 == 0:
                print(f"  epoch {epoch} step {s}/{steps_per_epoch} loss {total / n:.5f}", flush=True)
        model.eval()
        with torch.no_grad():
            val_loss = loss_fn(v_us, v_them, v_bucket, v_target, v_weight).item()
            seen_loss = loss_fn(s_us, s_them, s_bucket, s_target, s_weight).item()
        improved = val_loss < best_val
        print(f"epoch {epoch:2d}/{args.epochs}  train {total / max(n, 1):.5f}  seen {seen_loss:.5f}"
              f"  held-out {val_loss:.5f}{'  (best)' if improved else ''}  ({time.time() - start:.0f}s)", flush=True)
        os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
        if improved:
            best_val, best_epoch = val_loss, epoch
            # <out>.pt always holds the best epoch so far (the next generation continues from it).
            torch.save(model.state_dict(), args.out + ".pt.tmp")
            os.replace(args.out + ".pt.tmp", args.out + ".pt")
        # Full state for --resume, written atomically (a power cut mid-write can't corrupt it).
        torch.save({"model": model.state_dict(), "opt": opt.state_dict(), "sched": sched.state_dict(),
                    "rng": rng.bit_generator.state, "step": step, "epoch": epoch,
                    "best_val": best_val, "best_epoch": best_epoch}, ckpt_path + ".tmp")
        os.replace(ckpt_path + ".tmp", ckpt_path)
        if step >= total_steps:
            break
        if args.patience and epoch - best_epoch >= args.patience:
            print(f"stopping early: held-out loss has not improved for {args.patience} epochs", flush=True)
            break

    # Export the best epoch, not the last one.
    if best_epoch:
        model.load_state_dict(torch.load(args.out + ".pt"))
        print(f"best epoch {best_epoch}: held-out loss {best_val:.5f}", flush=True)
    net_path = args.out + ".nnue"
    worst = write_golden(model, layout, val, args.out + ".golden.txt")
    write_nnue(model, layout, net_path + ".tmp")
    os.replace(net_path + ".tmp", net_path)
    if os.path.exists(ckpt_path):
        os.remove(ckpt_path)
    print(f"wrote {args.out}.nnue  (quantisation error on 200 positions: max {worst:.1f} cp)")


if __name__ == "__main__":
    main()
