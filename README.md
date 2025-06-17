# 🗄️ cab4j - Cabinet Files in pure Java

A Java library for creating and reading cabinet files (`*.cab`) with Java using NIO ByteBuffer. 
It was created using the [official Microsoft CAB Documentation](docu/%5BMS-CAB%5D.pdf) and has almost no dependencies.

# ✨ Features
- Checksum supported
- makes only use of Java NIO2 ByteBuffers
- Uncompressed, MSZIP, Quantum and LZX compression support
- Recursive directory packing with subfolder support
- Splitting archives into multiple cabinets
- Java 8+ compatible

# 📃 Testing and official docs
- Test files are located in `test/`-Directory, this contains the test cab from the pdf in `docu/` directory


## File attributes

`CabFile.addFile(Path)` automatically records DOS file attributes like read-only or hidden when available. You can also specify attributes manually using

```java
cab.addFile("name.txt", buffer, CfFile.ATTRIB_READONLY | CfFile.ATTRIB_HIDDEN);
```

Use `CabExtractor.extractWithAttributes` to retrieve these attributes or
`extractToDirectory` to restore them on disk.

## Directory packing

You can add entire directory trees using `CabFile.addDirectory(Path)`. Relative
paths are preserved inside the cabinet. When building large archives you can
split the output into multiple cabinets:

```java
CabFile cab = new CabFile();
cab.addDirectory(Paths.get("assets"));
List<ByteBuffer> cabs = cab.createCabinetSet(1_000_000); // split after 1 MB
```

## File timestamps

`CabFile.addFile(Path)` also preserves the last modified time of the source
file. When extracting with `extractWithAttributes` the returned
`ExtractedFile` contains this timestamp and `extractToDirectory` restores it on
disk. The time format follows the same semantics as the Java ZIP API.