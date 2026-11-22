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
has Neighbour Huffman, HuffmanWriter2's coding (and files).

HuffmanWriter2 and HuffmanReader2 have a Huffman coding of their own: the
deltas (least-squares or average, the **Delta** menu) are coded directly, with
no unary strings, by Huffman tables chosen from each delta's neighbours (how big
the deltas already coded to its left and above are), so the file needn't say
which table is used where. In the quietest parts runs of zeros are coded as one
symbol (as in lossless JPEG-LS) and other deltas two at a time. Its files are
smaller than HuffmanWriter's on the test photos (7-12% lossless, 1-5% at the
default quantization), and with the average deltas it codes and decodes about
twice as fast. It doesn't use Deflate, so the files don't depend on a zlib.
