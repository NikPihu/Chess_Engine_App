"""Quantises a trained v3 model into the .nnue file read by chess_engine_v3/src/nnue, and writes a
golden file (FEN + exact integer evaluation) so the C++ test can prove it computes exactly what
Python computes - including king buckets, mirroring and output heads.

  "NNV3" | int32 version=3 | int32 hidden | int32 king_buckets | int32 output_buckets | int32 mirror
  | int32 bucket_map[64] | int16 ft[KB*768][H] (x QA) | int16 ft_bias[H] (x QA)
  | int16 out[OB][2H] (x QB) | int32 out_bias[OB] (x QA*QB)                       little-endian
"""

import struct

import numpy as np
import torch

from features import batch_inputs, record_fen

QA, QB, SCALE = 255, 64, 400


def quantise(model):
    n = model.inputs
    ft_w = np.round(model.ft.weight.detach().numpy()[:n] * QA).astype(np.int16)  # [inputs, H]
    ft_b = np.round(model.ft_bias.detach().numpy() * QA).astype(np.int16)
    out_w = np.round(model.out.weight.detach().numpy() * QB).astype(np.int16)  # [OB, 2H]
    out_b = np.round(model.out.bias.detach().numpy().astype(np.float64) * QA * QB).astype(np.int64)
    return ft_w, ft_b, out_w, out_b


def write_nnue(model, layout, path):
    ft_w, ft_b, out_w, out_b = quantise(model)
    with open(path, "wb") as f:
        f.write(b"NNV3")
        f.write(struct.pack("<iiiii", 3, model.hidden, layout.king_buckets, layout.output_buckets,
                            1 if layout.mirror else 0))
        f.write(layout.bucket_map.astype("<i4").tobytes())
        f.write(ft_w.astype("<i2").tobytes())
        f.write(ft_b.astype("<i2").tobytes())
        f.write(out_w.astype("<i2").tobytes())
        f.write(out_b.astype("<i4").tobytes())


def tdiv(a, b):
    """Integer division truncating toward zero, like C++ (exact for any size, unlike float)."""
    q = abs(a) // abs(b)
    return q if (a >= 0) == (b > 0) else -q


def quantised_eval(q, us, them, bucket, hidden):
    """Integer evaluation exactly as chess_engine_v3's Network::evaluate computes it."""
    ft_w, ft_b, out_w, out_b = q

    def acc(features):
        a = ft_b.astype(np.int64).copy()
        for f in features:
            if f < len(ft_w):
                a += ft_w[f]
        return a

    def screlu(a):
        c = np.clip(a, 0, QA)
        return c * c

    w = out_w[bucket].astype(np.int64)
    total = int(np.sum(screlu(acc(us)) * w[:hidden])) + int(np.sum(screlu(acc(them)) * w[hidden:]))
    output = tdiv(total, QA) + int(out_b[bucket])
    return tdiv(output * SCALE, QA * QB)


def write_golden(model, layout, records, path, count=200):
    """Lines: 'fen;expected integer centipawns (side to move);float centipawns'."""
    q = quantise(model)
    recs = records[:count]
    us, them, bucket = batch_inputs(layout, recs)
    with torch.no_grad():
        floats = model(torch.from_numpy(us), torch.from_numpy(them), torch.from_numpy(bucket)).numpy() * SCALE
    worst = 0.0
    with open(path, "w") as f:
        for i in range(len(recs)):
            expected = quantised_eval(q, us[i], them[i], int(bucket[i]), model.hidden)
            worst = max(worst, abs(expected - floats[i]))
            f.write(f"{record_fen(recs[i])};{expected};{floats[i]:.2f}\n")
    return worst
