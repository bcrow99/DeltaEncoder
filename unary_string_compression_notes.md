# Why unary "string" packing sometimes beats plain integer entropy coding (and sometimes doesn't)

## The core idea

`getShannonLimit` doesn't measure entropy in the abstract — it measures the total bit
cost of a specific stream under a specific alphabet:

```
limit = -sum( freq[i] * log2( freq[i] / total ) )
```

The program computes this twice on the *same underlying data*, but over two different
alphabets:

- **`int_bitlength`** — entropy of the raw pixel values (a handful of symbols: 0–3ish)
- **`string_bitlength`** — entropy of the *bytes* produced after unary-coding those
  pixel values (rank 0 = most frequent value, coded as fewest bits) and packing the
  result into 8-bit bytes, with an extra bitwise run-compression pass applied first

Unary coding + byte packing is a lossless reshaping of the same information — it can't
create or destroy information, but it *can* change how that information lines up with
byte boundaries. And Shannon-limit entropy only "sees" whatever alphabet you hand it.
That's the entire story.

## Case A — structured / run-heavy data → strings win

Long runs of the same pixel value produce a unary bitstream that is itself periodic
(e.g. constant `0`s, or a repeating `10101010...`). Periodic bit patterns collapse onto
a handful of distinct byte values, repeated constantly. A histogram with a few values
and huge counts each has *low* entropy — so the byte view costs fewer bits than the
raw-integer view.

## Case B — noisy / unpredictable data → integers win

With no runs, every unary code has a different length, so byte boundaries land in a
different phase every time. That desync smears out whatever mild skew existed at the
pixel level, and the byte histogram ends up close to flat across all 256 values — near
the 8-bits/byte ceiling. That's *more* expensive than the compact, low-alphabet
raw-integer view.

## The extra wrinkle: the bitwise run-compression pass

Before the unary bytes get histogrammed, `compressStrings` applies one more
run-collapsing pass directly on the bits (not bytes), repeated up to 15 times, and only
kept if it saves ≥5%. It doesn't change the story above — it **amplifies** it:

- On structured data, it eats further into the existing runs → string-side advantage
  gets bigger.
- On noisy data, `getCompressionAmount` comes back non-positive almost immediately, the
  loop bails, and the original unary bytes are returned untouched → no effect
  (`getIterations` returns 0, i.e. "String was not compressed").

## Bottom line

Neither method is universally better — each is optimal for a different shape of
redundancy:

- **Redundancy in the *value distribution*** (a few values, skewed frequencies,
  regardless of order) → plain per-symbol entropy coding on the raw integers wins.
- **Redundancy in the *sequence/run structure*** (repeats and periodicity that
  order-blind integer entropy coding can't see at all) → unary coding + byte packing +
  bitwise run compression wins.

---

# The two bitwise run-compression transforms

Both operate on the unary-coded bitstream (rank `r` = `r` one-bits then a terminating
zero: `0`, `10`, `110`, `1110`, ...). Each scans left to right, pairs up bits, and
shrinks runs of **one specific bit value** while expanding the other.

### Stop-bit transform — `compressZeroBits` (compresses runs of `0`)

```
00  -->  0        (pair of zeros shrinks)
01  -->  11        (grows by 1 bit)
1   -->  10         (an isolated 1 grows by 1 bit)
```

### Run-bit transform — `compressOneBits` (compresses runs of `1`)

```
11  -->  1        (pair of ones shrinks)
10  -->  01        (grows by 1 bit)
0   -->  00         (an isolated 0 grows by 1 bit)
```

These are structural mirror images of each other — same logic, roles of `0` and `1`
swapped.

## Pure runs — the ideal case

```
compressZeroBits:
00000000  -->  0000       (four "00" pairs, each collapses to one "0")

compressOneBits:
11111111  -->  1111       (four "11" pairs, each collapses to one "1")
```

Every pair shrinks by half. Repeated application (up to 15 passes) shrinks a long run
further and further, geometrically.

## Why the other bit expands

Whichever bit isn't being run-compressed has to grow, to keep the encoding
self-terminating and lossless. Example — three rank-0 codes, one rank-1 code, two more
rank-0:

```
compressZeroBits on "0001000"
  pairs:   00 | 01 | 00 | 0 (trailing, alone)
  output:   0 | 11 | 0  | 1
  result:  01101          (7 bits --> 5 bits)
```

The `00` pairs shrink as expected, but the single stray `1` (paired as `01`) grows to
`11`. Good trade if `0`s dominate; backfires if `1`s are common.

A string with no real runs at all doesn't compress under either transform — it just
gets relabeled:

```
compressZeroBits on "01010101"  -->  11111111   (8 bits --> 8 bits, no savings)
compressOneBits  on "10101010"  -->  01010101   (same story, mirrored)
```

## Why both transforms exist

They're tuned to opposite regimes of the same unary alphabet:

- **`compressZeroBits`** wins when **`0`s (stop bits) dominate** — i.e. the string is
  full of *low-rank* symbols. Rank 0's entire code is a single `0`, so a run of the
  most-frequent symbol is a run of nothing but stop bits — exactly what this transform
  shrinks. (This is the banded test image: half the pixels are rank 0, in long runs.)
- **`compressOneBits`** wins when **`1`s (run bits) dominate** — i.e. *higher-rank*
  symbols are common. Each higher-rank code spends most of its length on `1`s before a
  single terminating `0`, so runs of mid/high-rank symbols produce long `1`-runs.

`compressStrings` picks between them per input using `getZeroRatio` (majority bit value
picks the matching transform) — which is why whether unary+bitwise compression helps at
all depends on the shape of the rank distribution and the run structure of the data,
not on any fixed property of unary coding in general.
