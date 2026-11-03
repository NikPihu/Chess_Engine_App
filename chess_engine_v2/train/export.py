"""Quantises a trained model into the .nnue file read by nnue/NetFile.java, and writes a golden
file so a Java test can prove that Java computes exactly what Python computes.

  "NNUE" | int32 version=1 | int32 hidden=256
  int16 featureWeights[768*256] (x QA) | int16 featureBias[256] (x QA)
  int16 outputWeights[512] (x QB) | int32 outputBias (x QA*QB)        little-endian
"""

import struct

import numpy as np
import torch

from model import HIDDEN, Nnue

QA, QB, SCALE = 255, 64, 400


def quantise(model: Nnue):
    ft_w = np.round(model.ft.weight.detach().numpy()[:768] * QA).astype(np.int16)  # [768,256]
    ft_b = np.round(model.ft_bias.detach().numpy() * QA).astype(np.int16)
    out_w = np.round(model.out.weight.detach().numpy().reshape(-1) * QB).astype(np.int16)
    out_b = int(round(float(model.out.bias.detach().numpy()[0]) * QA * QB))
    return ft_w, ft_b, out_w, out_b


def write_nnue(model: Nnue, path):
    ft_w, ft_b, out_w, out_b = quantise(model)
    with open(path, "wb") as f:
        f.write(b"NNUE")
        f.write(struct.pack("<ii", 1, HIDDEN))
        f.write(ft_w.astype("<i2").tobytes())
        f.write(ft_b.astype("<i2").tobytes())
        f.write(out_w.astype("<i2").tobytes())
        f.write(struct.pack("<i", out_b))


def quantised_eval(q, us, them):
    """Integer evaluation, written the same way as Network.java (to cross-check)."""
    ft_w, ft_b, out_w, out_b = q

    def acc(features):
        a = ft_b.astype(np.int64).copy()
        for f in features:
            if f != 768:
                a += ft_w[f]
        return a

    def screlu(a):
        c = np.clip(a, 0, QA)
        return c * c

    total = int(np.sum(screlu(acc(us)) * out_w[:HIDDEN].astype(np.int64)))
    total += int(np.sum(screlu(acc(them)) * out_w[HIDDEN:].astype(np.int64)))
    output = int(total / QA) + out_b  # Java: long division truncates toward zero
    return int(output * SCALE / (QA * QB))


def write_golden(model: Nnue, us, them, path, count=200):
    """Lines: 'us features;them features;expected integer centipawns;float centipawns'."""
    q = quantise(model)
    with torch.no_grad():
        floats = model(torch.from_numpy(us[:count]), torch.from_numpy(them[:count])).numpy() * SCALE
    worst = 0.0
    with open(path, "w") as f:
        for i in range(min(count, len(us))):
            expected = quantised_eval(q, us[i], them[i])
            worst = max(worst, abs(expected - floats[i]))
            f.write(
                ",".join(str(int(x)) for x in us[i]) + ";"
                + ",".join(str(int(x)) for x in them[i]) + ";"
                + f"{expected};{floats[i]:.2f}\n"
            )
    return worst
