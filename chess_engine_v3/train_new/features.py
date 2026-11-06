"""v3 network inputs, computed from v2's 72-byte training records (chess_engine_v2/datagen/Record.java).

Must match chess_engine_v3/src/nnue/nnue.{h,cpp} exactly:

  for perspective P:
    square s   -> s ^ 56 for black (each side sees itself at the bottom)
    king       -> P's king square, oriented the same way
    mirror     -> if the king is on files e-h, every square ^ 7 (left-right mirror)
    bucket     -> BUCKET_MAP[mirrored king square]
    feature    =  bucket * 768 + relation * 384 + pieceType * 64 + square
  output head = clamp((pieces - 2) // 4, 0, OB - 1)

A record stores up to 32 features from WHITE's view (v2 layout: relation * 384 + type * 64 + sq,
relation 0 = white piece), 768 = unused. Everything here is vectorised numpy over a whole batch.
"""

import numpy as np

V2_PAD = 768
KING = 5

# Bucket layout for the 32 queenside squares (files a-d after mirroring), rank 1 at the top:
#   rank 1: a b -> 0, c d -> 1     (castled long / king at home)
#   rank 2:        -> 2
#   rank 3-8:      -> 3            (king has walked up: endgames, attacks)
BUCKETS_4 = np.zeros(64, dtype=np.int64)
for sq in range(64):
    file, rank = sq % 8, sq // 8
    if rank == 0:
        BUCKETS_4[sq] = 0 if file in (0, 1, 6, 7) else 1
    elif rank == 1:
        BUCKETS_4[sq] = 2
    else:
        BUCKETS_4[sq] = 3
BUCKETS_1 = np.zeros(64, dtype=np.int64)


class Layout:
    """Shape of a v3 network's inputs and output heads."""

    def __init__(self, king_buckets=4, output_buckets=8, mirror=True):
        assert king_buckets in (1, 4)
        self.king_buckets = king_buckets
        self.output_buckets = output_buckets
        self.mirror = mirror
        self.bucket_map = BUCKETS_4 if king_buckets == 4 else BUCKETS_1
        self.inputs = king_buckets * 768
        self.pad = self.inputs  # index of the all-zero "unused slot" row


def decode(white_features):
    """(relation, type, square, valid) arrays from v2 white-view features [B, 32]."""
    f = white_features.astype(np.int64)
    valid = f != V2_PAD
    fz = np.where(valid, f, 0)
    relation = fz // 384
    kind = (fz % 384) // 64
    sq = fz % 64
    return relation, kind, sq, valid


def king_squares(relation, kind, sq, valid):
    """White's and black's king squares [B] (in white's orientation)."""
    is_king = valid & (kind == KING)
    white = np.where(is_king & (relation == 0), sq, -1).max(axis=1)
    black = np.where(is_king & (relation == 1), sq, -1).max(axis=1)
    return white, black


def perspective_features(layout, relation, kind, sq, valid, king_sq, black_view):
    """Feature indices [B, 32] for one perspective; pad = layout.pad."""
    rel = relation ^ 1 if black_view else relation
    s = sq ^ 56 if black_view else sq
    k = king_sq ^ 56 if black_view else king_sq
    if layout.mirror:
        mirrored = (k % 8) >= 4
        k = np.where(mirrored, k ^ 7, k)
        s = np.where(mirrored[:, None], s ^ 7, s)
    bucket = layout.bucket_map[k]
    feats = bucket[:, None] * 768 + rel * 384 + kind * 64 + s
    return np.where(valid, feats, layout.pad)


def batch_inputs(layout, records):
    """(us, them, output_bucket) for a batch of records, from the side to move's view."""
    relation, kind, sq, valid = decode(records["features"])
    wk, bk = king_squares(relation, kind, sq, valid)
    white = perspective_features(layout, relation, kind, sq, valid, wk, False)
    black = perspective_features(layout, relation, kind, sq, valid, bk, True)
    stm_black = records["stm"] == 1
    us = np.where(stm_black[:, None], black, white)
    them = np.where(stm_black[:, None], white, black)
    pieces = valid.sum(axis=1)
    out_bucket = np.clip((pieces - 2) // 4, 0, layout.output_buckets - 1)
    return us, them, out_bucket


def record_fen(record):
    """Board + side to move as a FEN (castling/en passant don't matter to the network)."""
    relation, kind, sq, valid = decode(record["features"][None, :])
    board = ["."] * 64
    for r, k, s, v in zip(relation[0], kind[0], sq[0], valid[0]):
        if v:
            c = "PNBRQK"[k]
            board[s] = c if r == 0 else c.lower()
    rows = []
    for rank in range(7, -1, -1):
        row, empty = "", 0
        for file in range(8):
            c = board[rank * 8 + file]
            if c == ".":
                empty += 1
            else:
                row += (str(empty) if empty else "") + c
                empty = 0
        rows.append(row + (str(empty) if empty else ""))
    return "/".join(rows) + (" b" if record["stm"] == 1 else " w") + " - - 0 1"
