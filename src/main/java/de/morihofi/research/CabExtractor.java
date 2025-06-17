package de.morihofi.research;

import de.morihofi.research.structures.CfFile;
import de.morihofi.research.structures.CfFolder;
import de.morihofi.research.util.ChecksumHelper;
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

        ExtractedFile(ByteBuffer data, short attribs) {
            this.data = data;
            this.attribs = attribs;
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
            short attribs;
        }

        FileHeader[] files = new FileHeader[cFiles];
        for (int i = 0; i < cFiles; i++) {
            FileHeader fe = new FileHeader();
            fe.size = buffer.getInt();
            buffer.getInt(); // uoffFolderStart
            buffer.getShort(); // iFolder
            buffer.getShort(); // date
            buffer.getShort(); // time
            fe.attribs = buffer.getShort();
            StringBuilder sb = new StringBuilder();
            byte b;
            while ((b = buffer.get()) != 0) {
                sb.append((char) (b & 0xFF));
            }
            fe.name = sb.toString();
            files[i] = fe;
        }

        // Read CFDATA blocks (assume 1 per folder)
        int[] csum = new int[cFolders];
        short[] cbData = new short[cFolders];
        short[] cbUncomp = new short[cFolders];
        for (int i = 0; i < cFolders; i++) {
            csum[i] = buffer.getInt();
            cbData[i] = buffer.getShort();
            cbUncomp[i] = buffer.getShort();
        }

        Map<String, ExtractedFile> result = new LinkedHashMap<>();
        if (cFolders > 0) {
            ByteBuffer dataSlice = buffer.slice();
            dataSlice.limit(Short.toUnsignedInt(cbData[0]));

            ByteBuffer checksumBuf = ByteBuffer.allocate(Short.toUnsignedInt(cbData[0]) + 4);
            checksumBuf.order(ByteOrder.LITTLE_ENDIAN);
            checksumBuf.putShort(cbData[0]);
            checksumBuf.putShort(cbUncomp[0]);
            checksumBuf.put(dataSlice.duplicate());
            checksumBuf.flip();
            int calculated = ChecksumHelper.cabChecksum(checksumBuf);
            if (calculated != csum[0]) {
                throw new IllegalStateException("CFDATA checksum mismatch");
            }

            CfFolder.COMPRESS_TYPE comp = CfFolder.COMPRESS_TYPE.fromValue(Short.toUnsignedInt(folderTypeCompress[0]));
            ByteBuffer uncompressed;
            switch (comp) {
                case TCOMP_TYPE_NONE:
                    uncompressed = ByteBuffer.allocate(Short.toUnsignedInt(cbUncomp[0]));
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
                    byte[] out = new byte[Short.toUnsignedInt(cbUncomp[0])];
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
                default:
                    throw new UnsupportedOperationException("Unsupported compression type: " + comp);
            }

            buffer.position(buffer.position() + Short.toUnsignedInt(cbData[0]));
            ByteBuffer tmp = uncompressed.duplicate();
            for (FileHeader fe : files) {
                ByteBuffer slice = tmp.slice();
                slice.limit(fe.size);
                ByteBuffer data = ByteBuffer.allocate(fe.size);
                data.put(slice);
                data.flip();
                tmp.position(tmp.position() + fe.size);
                result.put(fe.name, new ExtractedFile(data, fe.attribs));
            }
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
