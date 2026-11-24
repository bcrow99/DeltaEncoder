# DeltaEncoder
Compressing images by quantizing data, extracting delta values, and then doing compression on the delta values.  Originally the focus was on the deltas, but now concerned more with the entropy encoding. 

## Build and run

Compile the `.java` files with any JDK 8 or later, then run the programs
directly:

```
javac *.java
java DeltaWriter image.png         # compress an image (Save writes foo)
java DeltaWriter image.png out.dw  # ... Save writes out.dw
java DeltaReader foo               # view a compressed file
```

The writers take an image file (PNG, JPEG, GIF or BMP), or open a file chooser if
you don't give one, and optionally after it the file to save to. **File > Save**
writes the compressed image to that file, or if none was given to a file called
`foo` in the current folder; the matching reader opens it. In SimpleWriter,
SimpleWriter2, HuffmanWriter, HuffmanWriter2, GenericWriter and PixelWriter,
**File > Decode Check** (off unless switched on) makes Save also decode what it coded and say
whether it comes back the same: Save is faster without it, and if a file doesn't
decode right in its reader, switching it on tells whether the writer or the
reader is at fault.

| Writer | Reader |
|---|---|
| SimpleWriter | SimpleReader |
| SimpleWriter2 | SimpleReader2 |
| HuffmanWriter | HuffmanReader |
| HuffmanWriter2 | HuffmanReader2 |
| GenericWriter | GenericReader |
| PacketWriter | PacketReader |
| DeltaWriter, DeltaWriter2 | DeltaReader |
| BlockWriter | BlockReader |

GenericWriter and GenericReader are NativeEncoder's NativeWriter and
NativeReader in Java alone (no C library to build): SimpleWriter2's and
HuffmanWriter's codings in one program (the **Entropy** menu), each with the
least-squares deltas or the average filter (the **Delta** menu), in up to 64
bands. They write and read the same files as NativeWriter and NativeReader, byte
for byte; with the least-squares deltas those are SimpleWriter2's and
HuffmanWriter's files, so GenericReader opens those too. The Entropy menu also
has Huffman tables (neighbour Huffman), HuffmanWriter2's coding (and files),
and Context tables (ContextTables): the context aware coding's contexts with
fixed tables in place of its adaptive models. Each channel's deltas are
counted once by context (one model's contexts, chosen, with whether the
earlier channels' cross buckets are merged and how finely the tables are
written, by the coded size), the counts written as tables, and the deltas
rANS coded with them (a table lookup and a multiply a symbol, no division).
The files are about 1-3% bigger than context aware's (smaller than both
Huffman codings'), and decode about twice as fast (Context tables format 'T'
1).

HuffmanWriter2 and HuffmanReader2 have a Huffman coding of their own: the
deltas (least-squares or average, the **Delta** menu) are coded directly, with
no unary strings, by Huffman tables chosen from each delta's neighbours (how big
the deltas already coded to its left and above are), so the file needn't say
which table is used where. In the quietest parts runs of zeros are coded as one
symbol (as in lossless JPEG-LS) and other deltas two at a time. A delta coded
on its own has its sign flipped where its neighbours' (left + above) is
negative, so the tables can give their short codes to the way the
neighbours go (format version 2: about 1-3% smaller with the average deltas,
a few tenths of a percent with least squares). Version 3 adds the context
aware coding's second opinion: how far the median edge detector is from the
prediction (its JPEG-LS gradient level, 0 to 4 either way) picks the tables
too, flips a delta's sign where it's negative and breaks the run mode where
it isn't 0 (about 2% smaller, up to 5% on the finch; the decoder needs the
values for it, so it decodes a pixel at a time as the channel is rebuilt).
Versions 1 and 2 files are still read. Its files are
smaller than HuffmanWriter's on the test photos (7-12% lossless, 1-5% at the
default quantization), and with the average deltas it codes and decodes about
twice as fast. It doesn't use Deflate, so the files don't depend on a zlib.

HuffmanWriter's and GenericWriter's Huffman files (Huffman formats 3 and 4)
don't use Deflate either: their code lengths are coded the same compact way.
HuffmanReader, GenericReader and NativeReader still read the earlier versions
(1 and 2), whose code lengths were Deflated.
