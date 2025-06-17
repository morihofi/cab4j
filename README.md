# cabinet4j

A Java library for creating cabinet files (`*.cab`) with Java using NIO ByteBuffer. 
It was created using the [official Microsoft CAB Documentation](docu/%5BMS-CAB%5D.pdf) and doesn't have any dependencies.

- Checksum generation supported
- Test files are located in `test/`-Directory, this contains the test cab from the pdf in `doku/` directory
- This implementation makes use of only Java ByteBuffers
- Currently, there is no compression support. Just archiving the files as they're on disk
- Uncompressed and MSZIP compression support

# Disclaimer
This version is NOT production ready, 'cause it contains a few bugs and some miscalculated offsets

# Future goals
- reproduce the test files 1:1
- support compression 
- support extracting
- support multiple disks
- Verification of file limits
- Attribute support
- Folder support

## File attributes

`CabFile.addFile(Path)` automatically records DOS file attributes like read-only or hidden when available. You can also specify attributes manually using

```java
cab.addFile("name.txt", buffer, CfFile.ATTRIB_READONLY | CfFile.ATTRIB_HIDDEN);
```

Use `CabExtractor.extractWithAttributes` to retrieve these attributes or
`extractToDirectory` to restore them on disk.