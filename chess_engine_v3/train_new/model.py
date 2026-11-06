"""The v3 network in PyTorch. Must match chess_engine_v3/src/nnue (same shape, same activation).

  (768 x KB king-bucket features -> H) x 2 perspectives -> SCReLU -> 2H -> OB output heads
  output 1.0 = 400 centipawns; the head is chosen by the number of pieces on the board.
"""

import torch
from torch import nn

CLIP = 1.98  # keeps quantised weights (x255 / x64) and accumulator sums inside int16


def screlu(x):
    return torch.clamp(x, 0.0, 1.0) ** 2


class NnueV3(nn.Module):
    def __init__(self, inputs, hidden=768, output_buckets=8):
        super().__init__()
        self.inputs = inputs
        self.hidden = hidden
        self.output_buckets = output_buckets
        # One H-vector per feature; a position = sum of its active features' vectors (+ bias).
        # padding_idx = inputs: the extra row for unused slots stays exactly zero.
        self.ft = nn.EmbeddingBag(inputs + 1, hidden, mode="sum", padding_idx=inputs)
        self.ft_bias = nn.Parameter(torch.zeros(hidden))
        self.out = nn.Linear(2 * hidden, output_buckets)
        with torch.no_grad():
            self.ft.weight.normal_(0.0, 0.05)
            self.ft.weight[inputs].zero_()
            self.ft_bias.fill_(0.2)
            self.out.weight.normal_(0.0, 0.01)
            self.out.bias.zero_()

    def forward(self, us, them, bucket):
        a = self.ft(us) + self.ft_bias
        b = self.ft(them) + self.ft_bias
        all_heads = self.out(torch.cat([screlu(a), screlu(b)], dim=1))  # [B, OB]
        return all_heads.gather(1, bucket.unsqueeze(1)).squeeze(1)

    def clip_(self):
        with torch.no_grad():
            self.ft.weight.clamp_(-CLIP, CLIP)
            self.out.weight.clamp_(-CLIP, CLIP)
