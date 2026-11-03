"""Training data: reads the 72-byte records written by datagen/Record.java.

Must stay in sync with Record.java and nnue/Features.java:
  features: 32 x uint16 from WHITE's perspective, 768 = unused slot
  score: int16 centipawns, white's point of view
  stm: 0 white to move, 1 black to move
  result: white's point of view, 0 loss / 1 draw / 2 win
  weight: sample weight (Lichess blunder positions count more)
"""

import numpy as np

PAD = 768
RECORD = np.dtype(
    [
        ("features", "<u2", (32,)),
        ("score", "<i2"),
        ("stm", "u1"),
        ("result", "u1"),
        ("weight", "u1"),
        ("reserved", "u1", (3,)),
    ]
)
assert RECORD.itemsize == 72


def load(paths, max_share=None):
    """Concatenates record files. max_share = {path: fraction} caps a file's share of the mix."""
    parts = []
    for p in paths:
        recs = np.memmap(p, dtype=RECORD, mode="r")
        parts.append(recs)
    data = np.concatenate(parts) if len(parts) > 1 else np.array(parts[0])
    return data


def flip(white_features):
    """The same features seen from black's side (mirror of nnue.Features.flip)."""
    f = white_features.astype(np.int64)
    pad = f == PAD
    relation = f // 384
    rest = f % 384
    kind = rest // 64
    sq = rest % 64
    flipped = (relation ^ 1) * 384 + kind * 64 + (sq ^ 56)
    flipped[pad] = PAD
    return flipped


def batch_arrays(records, wdl_lambda):
    """Turns records into (us, them, target, weight) arrays from the side to move's view."""
    white = records["features"].astype(np.int64)
    black = flip(white)
    stm_black = records["stm"] == 1
    us = np.where(stm_black[:, None], black, white)
    them = np.where(stm_black[:, None], white, black)

    sign = np.where(stm_black, -1.0, 1.0)
    score = records["score"].astype(np.float32) * sign
    result = records["result"].astype(np.float32) / 2.0
    result = np.where(stm_black, 1.0 - result, result)

    target = wdl_lambda * sigmoid(score / 400.0) + (1.0 - wdl_lambda) * result
    weight = records["weight"].astype(np.float32)
    return us, them, target.astype(np.float32), weight


def sigmoid(x):
    return 1.0 / (1.0 + np.exp(-x))
