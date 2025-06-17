package de.morihofi.cab4j;

import de.morihofi.cab4j.structures.CfFile;
import de.morihofi.cab4j.structures.CfFolder;
import de.morihofi.cab4j.util.ChecksumHelper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.LinkedHashMap;
import java.util.Map;

public class CabExtractor {

    public static class ExtractedFile {
        public final ByteBuffer data;
        public final short attribs;
        public final java.time.LocalDateTime lastModified;

        ExtractedFile(ByteBuffer data, short attribs, java.time.LocalDateTime lm) {
            this.data = data;
            this.attribs = attribs;
            this.lastModified = lm;
        }
    }

    private static Map<String, ExtractedFile> extractInternal(ByteBuffer cabBuffer) {
        ByteBuffer buffer = cabBuffer.duplicate();
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        byte[] signature = new byte[4];
        buffer.get(signature);
        if (signature[0] != 'M' || signature[1] != 'S' || signature[2] != 'C' || signature[3] != 'F') {
            throw new IllegalArgumentException("Invalid CAB file");
        }

        buffer.getInt(); // reserved1
        buffer.getInt(); // cbCabinet
        buffer.getInt(); // reserved2
        int coffFiles = buffer.getInt();
        buffer.getInt(); // reserved3
        buffer.get(); // version minor
        buffer.get(); // version major
        short cFolders = buffer.getShort();
        short cFiles = buffer.getShort();
        buffer.getShort(); // flags
        buffer.getShort(); // setID
        buffer.getShort(); // iCabinet

        // folders (only first is used)
        // TODO: support multiple folders
        int[] folderCoffCabStart = new int[cFolders];
        short[] folderCCfData = new short[cFolders];
        short[] folderTypeCompress = new short[cFolders];
        for (int i = 0; i < cFolders; i++) {
            folderCoffCabStart[i] = buffer.getInt();
            folderCCfData[i] = buffer.getShort();
            folderTypeCompress[i] = buffer.getShort();
        }

        buffer.position(coffFiles);

        class FileHeader {
            String name;
            int size;
            int uoffFolderStart;
            short iFolder;
            short attribs;
            java.time.LocalDateTime lastModified;
        }

        FileHeader[] files = new FileHeader[cFiles];
        for (int i = 0; i < cFiles; i++) {
            FileHeader fe = new FileHeader();
            fe.size = buffer.getInt();
            fe.uoffFolderStart = buffer.getInt();
            fe.iFolder = buffer.getShort();
            short d = buffer.getShort(); // date
            short t = buffer.getShort(); // time
            fe.lastModified = java.time.LocalDateTime.of(
                    CfFile.decodeDate(d), CfFile.decodeTime(t));
            fe.attribs = buffer.getShort();
            StringBuilder sb = new StringBuilder();
            byte b;
            while ((b = buffer.get()) != 0) {
                sb.append((char) (b & 0xFF));
            }
            fe.name = sb.toString();
            files[i] = fe;
        }

        Map<Integer, ByteBuffer> folders = new LinkedHashMap<>();
        for (int i = 0; i < cFolders; i++) {
            buffer.position(folderCoffCabStart[i]);
            int csum = buffer.getInt();
            short cbData = buffer.getShort();
            short cbUncomp = buffer.getShort();

            ByteBuffer dataSlice = buffer.slice();
            dataSlice.limit(Short.toUnsignedInt(cbData));

            ByteBuffer checksumBuf = ByteBuffer.allocate(Short.toUnsignedInt(cbData) + 4);
            checksumBuf.order(ByteOrder.LITTLE_ENDIAN);
            checksumBuf.putShort(cbData);
            checksumBuf.putShort(cbUncomp);
            checksumBuf.put(dataSlice.duplicate());
            checksumBuf.flip();
            int calculated = ChecksumHelper.cabChecksum(checksumBuf);
            if (calculated != csum) {
                throw new IllegalStateException("CFDATA checksum mismatch");
            }

            CfFolder.COMPRESS_TYPE comp = CfFolder.COMPRESS_TYPE.fromValue(Short.toUnsignedInt(folderTypeCompress[i]));
            ByteBuffer uncompressed;
            switch (comp) {
                case TCOMP_TYPE_NONE:
                    uncompressed = ByteBuffer.allocate(Short.toUnsignedInt(cbUncomp));
                    uncompressed.put(dataSlice.duplicate());
                    uncompressed.flip();
                    break;
                case TCOMP_TYPE_MSZIP:
                    if (dataSlice.remaining() < 2 || dataSlice.get() != 'C' || dataSlice.get() != 'K') {
                        throw new IllegalStateException("Invalid MSZIP signature");
                    }
                    byte[] compBytes = new byte[dataSlice.remaining()];
                    dataSlice.get(compBytes);
                    java.util.zip.Inflater inflater = new java.util.zip.Inflater(true);
                    inflater.setInput(compBytes);
                    byte[] out = new byte[Short.toUnsignedInt(cbUncomp)];
                    try {
                        int written = inflater.inflate(out);
                        if (written < out.length) {
                            byte[] tmp = new byte[out.length];
                            System.arraycopy(out, 0, tmp, 0, written);
                            out = java.util.Arrays.copyOf(tmp, written);
                        }
                    } catch (java.util.zip.DataFormatException e) {
                        throw new IllegalStateException("MSZIP decompression failed", e);
                    } finally {
                        inflater.end();
                    }
                    uncompressed = ByteBuffer.wrap(out);
                    break;
                case TCOMP_TYPE_QUANTUM:
                    byte[] qBytes = new byte[dataSlice.remaining()];
                    dataSlice.get(qBytes);
                    java.io.ByteArrayInputStream qbis = new java.io.ByteArrayInputStream(qBytes);
                    byte[] qOut = new byte[Short.toUnsignedInt(cbUncomp)];
                    try (org.tukaani.xz.XZInputStream qlz = new org.tukaani.xz.XZInputStream(qbis)) {
                        int qlen = qlz.read(qOut);
                        if (qlen < qOut.length) {
                            byte[] tmp = new byte[qlen];
                            System.arraycopy(qOut, 0, tmp, 0, qlen);
                            qOut = java.util.Arrays.copyOf(tmp, qlen);
                        }
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException("Quantum decompression failed", e);
                    }
                    uncompressed = ByteBuffer.wrap(qOut);
                    break;
                case TCOMP_TYPE_LZX:
                    byte[] lzxBytes = new byte[dataSlice.remaining()];
                    dataSlice.get(lzxBytes);
                    java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(lzxBytes);
                    byte[] lzxOut = new byte[Short.toUnsignedInt(cbUncomp)];
                    try (org.tukaani.xz.XZInputStream lz = new org.tukaani.xz.XZInputStream(bis)) {
                        int len = lz.read(lzxOut);
                        if (len < lzxOut.length) {
                            byte[] tmp = new byte[len];
                            System.arraycopy(lzxOut, 0, tmp, 0, len);
                            lzxOut = java.util.Arrays.copyOf(tmp, len);
                        }
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException("LZX decompression failed", e);
                    }
                    uncompressed = ByteBuffer.wrap(lzxOut);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported compression type: " + comp);
            }

            folders.put(i, uncompressed);
        }

        Map<String, ExtractedFile> result = new LinkedHashMap<>();
        for (FileHeader fe : files) {
            ByteBuffer folder = folders.get((int) fe.iFolder);
            if (folder == null) {
                throw new IllegalStateException("Missing folder data for iFolder " + fe.iFolder);
            }
            ByteBuffer dup = folder.duplicate();
            dup.position(fe.uoffFolderStart);
            ByteBuffer slice = dup.slice();
            slice.limit(fe.size);
            ByteBuffer data = ByteBuffer.allocate(fe.size);
            data.put(slice);
            data.flip();
            result.put(fe.name, new ExtractedFile(data, fe.attribs, fe.lastModified));
        }

        return result;
    }

    public static Map<String, ByteBuffer> extract(ByteBuffer cabBuffer) {
        Map<String, ExtractedFile> withAttribs = extractInternal(cabBuffer);
        Map<String, ByteBuffer> res = new LinkedHashMap<>();
        for (Map.Entry<String, ExtractedFile> e : withAttribs.entrySet()) {
            res.put(e.getKey(), e.getValue().data);
        }
        return res;
    }

    public static Map<String, ExtractedFile> extractWithAttributes(ByteBuffer cabBuffer) {
        return extractInternal(cabBuffer);
    }

    public static void extractToDirectory(ByteBuffer cabBuffer, Path outputDir, boolean restoreAttributes) throws IOException {
        Map<String, ExtractedFile> files = extractInternal(cabBuffer);
        for (Map.Entry<String, ExtractedFile> entry : files.entrySet()) {
            Path p = outputDir.resolve(entry.getKey());
            Files.createDirectories(p.getParent());
            ByteBuffer data = entry.getValue().data.duplicate();
            data.position(0);
            Files.write(p, toArray(data));
            if (restoreAttributes) {
                DosFileAttributeView view = Files.getFileAttributeView(p, DosFileAttributeView.class);
                if (view != null) {
                    short a = entry.getValue().attribs;
                    try {
                        view.setReadOnly((a & CfFile.ATTRIB_READONLY) != 0);
                        view.setHidden((a & CfFile.ATTRIB_HIDDEN) != 0);
                        view.setSystem((a & CfFile.ATTRIB_SYSTEM) != 0);
                        view.setArchive((a & CfFile.ATTRIB_ARCHIVE) != 0);
                        Files.setLastModifiedTime(p, java.nio.file.attribute.FileTime.from(entry.getValue().lastModified.atZone(java.time.ZoneId.systemDefault()).toInstant()));
                    } catch (IOException | UnsupportedOperationException ignored) {
                    }
                }
            }
        }
    }

    private static byte[] toArray(ByteBuffer buffer) {
        byte[] arr = new byte[buffer.remaining()];
        buffer.get(arr);
        return arr;
    }
}
