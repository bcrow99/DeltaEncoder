# DeltaEncoder
Compressing images by quantizing data, extracting delta values, and then doing compression on the delta values.  Originally the focus was on the deltas, but now concerned more with the entropy encoding. 

## Download and run

The newest build is always here (rebuilt automatically on every push):

**[DeltaEncoder.jar](https://github.com/bcrow99/DeltaEncoder/releases/download/latest-build/DeltaEncoder.jar)**

It runs on Java 8 or later; a Java runtime (JRE) is enough. To check what you have, run `java -version`.

```
java -jar DeltaEncoder.jar                        # lists the programs
java -jar DeltaEncoder.jar DeltaWriter image.png  # compress an image
java -jar DeltaEncoder.jar DeltaReader foo        # view a compressed file
```

The writers take an image file (PNG, JPEG, GIF or BMP), or open a file chooser if
you don't give one. **File > Save** writes the compressed image to a file called
`foo` in the current folder, which the matching reader opens.

| Writer | Reader |
|---|---|
| SimpleWriter | SimpleReader |
| PacketWriter | PacketReader |
| DeltaWriter, DeltaWriter2 | DeltaReader |
| BlockWriter | BlockReader |

Program names aren't case sensitive (`deltawriter` works too). To build it
yourself instead, compile the `.java` files with any JDK 8 or later
(`javac *.java`) and run the programs directly, e.g. `java DeltaWriter image.png`.
