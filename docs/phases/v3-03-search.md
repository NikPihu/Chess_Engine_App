# Engine v3, step 3: modern search techniques

**Folder:** [`chess_engine_v3/src/search`](../../chess_engine_v3/src/search) · **Previous:** [step 2, port](v3-02-port-search-nnue.md) · **Plan:** [v3 plan](v3-plan.md)

**The idea of this step:** v2's search already had the "big classic" features (alpha-beta, TT, null move, LMR). Step 3 adds the second generation of ideas that every strong engine since about 2015 uses. Each one has its own switch, so it can be tested or turned off:
```bash
tools\engine3 "setoption name Features value v3,-se" "position startpos" "go depth 16"
```
- **`v3`** is the default: everything below.
- **`proven`** is exactly v2's set.
- **Toggling a single technique:** `v3,-name` removes one and `proven,+name` adds one.

## What was added

### Board and TT
| Piece | What it does |
|---|---|
| **SEE** (`Position::see_ge`) | *Static exchange evaluation*: plays out every capture on one square, cheapest attacker first, including x-rays (the rook behind the rook). Answers "does this move lose material?" without searching |
| **Non-pawn keys** | One Zobrist key per side for its pieces other than pawns (used by correction history) |
| **TT buckets** | 4 entries per 64-byte cache line instead of 1 slot. The static eval is stored too. Entries use "key XOR data" checking, so a half-written entry from another thread (step 4) is never trusted. Old and shallow entries are evicted first |

### Search switches
| Switch | Technique | In one sentence |
|---|---|---|
| `hist2` | History gravity, maluses, capture history, SEE ordering | History scores saturate instead of overflowing. Quiet moves that were tried and failed get a penalty. Captures are ordered by victim + their own history, and losing captures (SEE < 0) go *after* the quiet moves |
| `conthist` | Continuation history | "How good is this move right after *that* move?" (1 and 2 plies back), a much sharper signal than plain history |
| `histlmr` | History-adjusted LMR | Reduce more in non-PV nodes and when not improving; reduce less for moves with good history |
| `improving` | Improving flag | If our static eval is better than two plies ago, prune less (RFP margin, LMP count) |
| `lmp` | Late move pruning | At depth ≤ 8 in non-PV nodes, stop trying quiet moves after `(3 + d²) / (improving ? 1 : 2)` of them |
| `fp` | Futility pruning | Skip quiet moves when `eval + 100 + 100·depth` is still below alpha |
| `seep` | SEE pruning | Skip captures losing more than `90·depth` and quiets losing more than `30·d²` |
| `qsee` | Qsearch SEE | Quiescence ignores captures that lose material |
| `qstt` | Qsearch TT | Quiescence reads and writes the transposition table |
| `tteval` | TT-refined eval | Reuse the TT's stored eval. If the TT score is a bound that says the eval is too low or too high, use it instead |
| `corrhist` | Correction history | Learn per pawn structure (and per side's piece set) how far the network's eval tends to be off, and correct it. Updated only from trusted, quiet search results |
| `se` | Singular extensions | If the TT move is much better than *every* other move (checked by a shallow search without it), search it one ply deeper, or two plies if it is far better. If even the other moves beat beta, cut immediately (*multi-cut*). If the TT move isn't special, search it slightly shallower |
| `iir` | Internal iterative reduction | A node with no TT move was never searched well, so search it one ply shallower and let the next iteration do it properly |
| `tm` | Smarter time management | Per iteration: stop early when the best move has been stable for several iterations and took most of the nodes. Think longer when the best move keeps changing or the score drops |

## How each was checked
```bash
tools\engine3 test
```
- **SEE:** 11 hand-made positions (free piece, even trade, defended pawn, x-ray rook stacks, quiet moves onto attacked squares).
- **Keys:** the non-pawn keys equal a recompute on every move of the random-game test.
- **Switches:** `Features` parsing is tested (`v3,-se,+check`, unknown names are rejected).
- **Search:** all existing search tests (mates, stalemate, determinism, UCI) pass with the new default.

### Time to depth (middlegame position 7 of perft, depth 15, one thread), adding one switch at a time

| Added | Nodes | Time (ms) |
|---|---|---|
| `proven` (v2) | 2,824,030 | 648 (measured while an SPRT was running, so slower) |
| +hist2 +conthist +histlmr +improving | 783,801 | 122 |
| +lmp +fp +seep | 250,055 | 64 |
| +qsee +qstt +tteval +corrhist | 84,735 | 38 |
| +se +iir +tm (searches critical lines deeper) | 284,262 | 119 |

**Why nodes per second drops** (6.5M → 2.3M): pruning removes the cheapest nodes, mostly quiescence leaves. The remaining nodes are the expensive ones (move generation, ordering, SEE). This is normal. What matters is strength per second, which is what the SPRT measures.

## Result
<!-- SPRT_RESULT -->

## Next: step 4 = Lazy SMP
- **Threads:** several search threads share the TT; each searches the same position with slightly different timing, so they fill the table for each other.
- **The test:** 4 threads vs 1 at the same time control.
