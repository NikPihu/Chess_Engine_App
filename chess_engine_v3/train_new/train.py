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


class Dataset:
    """Several memory-mapped record files seen as one array, with per-file sampling shares."""

    def __init__(self, main_paths, extras, rng):
        self.parts = []  # (memmap, number of samples to draw per epoch)
        main = [np.memmap(p, dtype=RECORD, mode="r") for p in main_paths]
        main_total = sum(len(m) for m in main)
        for m in main:
            self.parts.append((m, len(m)))
        for spec in extras:
            path, share = spec.rsplit(":", 1)
            share = float(share)
            m = np.memmap(path, dtype=RECORD, mode="r")
            # The extra set gets `share` of the final mix (sampled with replacement if small).
            want = int(main_total * share / (1 - share))
            self.parts.append((m, want))
            print(f"extra {path}: {len(m):,} records, {want:,} samples per epoch")
        self.rng = rng
        self.size = sum(n for _, n in self.parts)

    def epoch_index(self):
        """(part, row) pairs for one shuffled epoch."""
        part_ids, rows = [], []
        for i, (m, n) in enumerate(self.parts):
            r = self.rng.permutation(len(m))[:n] if n <= len(m) else self.rng.integers(0, len(m), n)
            part_ids.append(np.full(len(r), i, dtype=np.int8))
            rows.append(r)
        part_ids = np.concatenate(part_ids)
        rows = np.concatenate(rows)
        order = self.rng.permutation(len(rows))
        return part_ids[order], rows[order]

    def gather(self, part_ids, rows):
        out = np.empty(len(rows), dtype=RECORD)
        for i, (m, _) in enumerate(self.parts):
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
    args = ap.parse_args()

    torch.manual_seed(args.seed)
    torch.set_num_threads(args.threads)
    rng = np.random.default_rng(args.seed)
    layout = Layout(args.king_buckets, args.output_buckets, not args.no_mirror)

    paths = sorted({p for pattern in args.data for p in glob.glob(pattern)})
    if not paths:
        sys.exit("no data files match " + " ".join(args.data))
    print("data:", *paths, sep="\n  ")
    data = Dataset(paths, args.extra, rng)

    # A fixed validation sample (taken out of the same distribution; small enough to keep in RAM).
    vp, vr = data.epoch_index()
    n_val = min(100_000, data.size // 50)
    val = data.gather(vp[:n_val], vr[:n_val])
    v_us, v_them, v_bucket, v_target, v_weight = tensors(layout, val, args.wdl)
    print(f"{data.size:,} samples per epoch, {n_val:,} validation")

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
    ckpt_path = args.out + ".ckpt"
    if args.resume and os.path.exists(ckpt_path):
        ckpt = torch.load(ckpt_path, weights_only=False)
        model.load_state_dict(ckpt["model"])
        opt.load_state_dict(ckpt["opt"])
        sched.load_state_dict(ckpt["sched"])
        rng.bit_generator.state = ckpt["rng"]
        step = ckpt["step"]
        first_epoch = ckpt["epoch"] + 1
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
        print(f"epoch {epoch:2d}/{args.epochs}  train {total / max(n, 1):.5f}  val {val_loss:.5f}"
              f"  ({time.time() - start:.0f}s)", flush=True)
        os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
        torch.save(model.state_dict(), args.out + ".pt")
        # Full state for --resume, written atomically (a power cut mid-write can't corrupt it).
        torch.save({"model": model.state_dict(), "opt": opt.state_dict(), "sched": sched.state_dict(),
                    "rng": rng.bit_generator.state, "step": step, "epoch": epoch}, ckpt_path + ".tmp")
        os.replace(ckpt_path + ".tmp", ckpt_path)
        if step >= total_steps:
            break

    net_path = args.out + ".nnue"
    worst = write_golden(model, layout, val, args.out + ".golden.txt")
    write_nnue(model, layout, net_path + ".tmp")
    os.replace(net_path + ".tmp", net_path)
    if os.path.exists(ckpt_path):
        os.remove(ckpt_path)
    print(f"wrote {args.out}.nnue  (quantisation error on 200 positions: max {worst:.1f} cp)")


if __name__ == "__main__":
    main()
