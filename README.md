# DeltaEncoder
Compressing images by quantizing data, extracting delta values, and then doing compression on the delta values.  Originally the focus was on the deltas, but now concerned more with the entropy encoding. 

## Download and run

The newest build is always here (rebuilt automatically on every push):

**[DeltaEncoder.jar](https://github.com/bcrow99/DeltaEncoder/releases/download/latest-build/DeltaEncoder.jar)**

It runs on Java 8 or later; a Java runtime (JRE) is enough. To check what you have, run `java -version`.

```
java -jar DeltaEncoder.jar                        # lists the programs
java -jar DeltaEncoder.jar DeltaWriter image.png  # compress an image (Save writes foo)
java -jar DeltaEncoder.jar DeltaWriter image.png out.dw  # ... Save writes out.dw
java -jar DeltaEncoder.jar DeltaReader foo        # view a compressed file
```

The writers take an image file (PNG, JPEG, GIF or BMP), or open a file chooser if
you don't give one, and optionally after it the file to save to. **File > Save**
writes the compressed image to that file, or if none was given to a file called
`foo` in the current folder; the matching reader opens it. In SimpleWriter,
SimpleWriter2, HuffmanWriter, GenericWriter and PixelWriter, **File > Decode
Check** (off unless switched on) makes Save also decode what it coded and say
whether it comes back the same: Save is faster without it, and if a file doesn't
decode right in its reader, switching it on tells whether the writer or the
reader is at fault.

| Writer | Reader |
|---|---|
| SimpleWriter | SimpleReader |
| SimpleWriter2 | SimpleReader2 |
| HuffmanWriter | HuffmanReader |
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
HuffmanWriter's files, so GenericReader opens those too.

Program names aren't case sensitive (`deltawriter` works too). To build it
yourself instead, compile the `.java` files with any JDK 8 or later
(`javac *.java`) and run the programs directly, e.g. `java DeltaWriter image.png`.
