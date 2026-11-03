"""The network, in PyTorch. Must match nnue/Network.java exactly (same shape, same activation).

  768 features --(feature transformer, shared)--> 256 per perspective
  SCReLU (clamp to [0,1], square) on [side to move, opponent] = 512 values --> 1 output
  output 1.0 = 400 centipawns
"""

import torch
from torch import nn

FEATURES = 768
HIDDEN = 256
PAD = 768
CLIP = 1.98  # keeps quantised weights and accumulator sums inside int16 in Java


def screlu(x):
    return torch.clamp(x, 0.0, 1.0) ** 2


class Nnue(nn.Module):
    def __init__(self):
        super().__init__()
        # One 256-vector per feature; a position = sum of its active features' vectors.
        # EmbeddingBag(mode="sum") is exactly that, and padding_idx makes unused slots count 0.
        self.ft = nn.EmbeddingBag(FEATURES + 1, HIDDEN, mode="sum", padding_idx=PAD)
        self.ft_bias = nn.Parameter(torch.zeros(HIDDEN))
        self.out = nn.Linear(2 * HIDDEN, 1)
        with torch.no_grad():
            self.ft.weight.normal_(0.0, 0.05)
            self.ft.weight[PAD].zero_()
            self.ft_bias.fill_(0.2)
            self.out.weight.normal_(0.0, 0.02)
            self.out.bias.zero_()

    def forward(self, us, them):
        a = self.ft(us) + self.ft_bias
        b = self.ft(them) + self.ft_bias
        return self.out(torch.cat([screlu(a), screlu(b)], dim=1)).squeeze(1)

    def clip_(self):
        with torch.no_grad():
            self.ft.weight.clamp_(-CLIP, CLIP)
            self.out.weight.clamp_(-CLIP, CLIP)
