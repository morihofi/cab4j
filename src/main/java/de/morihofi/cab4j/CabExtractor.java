package de.morihofi.cab4j;

import de.morihofi.cab4j.structures.CfFile;
import de.morihofi.cab4j.structures.CfFolder;
import de.morihofi.cab4j.util.ChecksumHelper;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
            CfFolder.COMPRESS_TYPE comp = CfFolder.COMPRESS_TYPE.fromValue(Short.toUnsignedInt(folderTypeCompress[i]));
            java.io.ByteArrayOutputStream folderOut = new java.io.ByteArrayOutputStream();

            for (int j = 0; j < Short.toUnsignedInt(folderCCfData[i]); j++) {
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

                byte[] arr = new byte[uncompressed.remaining()];
                uncompressed.duplicate().get(arr);
                folderOut.write(arr, 0, arr.length);

                buffer.position(buffer.position() + Short.toUnsignedInt(cbData));
            }

            folders.put(i, ByteBuffer.wrap(folderOut.toByteArray()));
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

    /**
     * Extracts a cabinet from the supplied {@link ReadableByteChannel} directly
     * to the given output directory using streaming and without holding the full
     * file contents in memory. Only uncompressed cabinets created by the
     * streaming {@link de.morihofi.cab4j.generator.CabGenerator} are supported.
     */
    public static void extractToDirectory(ReadableByteChannel in, Path outputDir) throws IOException {
        ByteBuffer hdr = ByteBuffer.allocate(36);
        readFully(in, hdr);
        hdr.order(ByteOrder.LITTLE_ENDIAN);
        hdr.flip();
        byte[] sig = new byte[4];
        hdr.get(sig);
        if (sig[0] != 'M' || sig[1] != 'S' || sig[2] != 'C' || sig[3] != 'F') {
            throw new IllegalArgumentException("Invalid CAB file");
        }
        hdr.getInt(); // reserved1
        hdr.getInt(); // cbCabinet
        hdr.getInt(); // reserved2
        int coffFiles = hdr.getInt();
        hdr.getInt(); // reserved3
        hdr.get(); // version minor
        hdr.get(); // version major
        short cFolders = hdr.getShort();
        short cFiles = hdr.getShort();
        hdr.getShort(); // flags
        hdr.getShort(); // setID
        hdr.getShort(); // iCabinet

        int[] folderCoffCabStart = new int[cFolders];
        int[] folderCCfData = new int[cFolders];
        short[] folderType = new short[cFolders];
        for (int i = 0; i < cFolders; i++) {
            ByteBuffer fb = ByteBuffer.allocate(8);
            fb.order(ByteOrder.LITTLE_ENDIAN);
            readFully(in, fb);
            fb.flip();
            folderCoffCabStart[i] = fb.getInt();
            folderCCfData[i] = Short.toUnsignedInt(fb.getShort());
            folderType[i] = fb.getShort();
        }

        // read file headers
        class FileInfo {
            String name;
            int size;
            int uoff;
            short folder;
        }
        FileInfo[] infos = new FileInfo[cFiles];
        for (int i = 0; i < cFiles; i++) {
            ByteBuffer fb = ByteBuffer.allocate(16);
            fb.order(ByteOrder.LITTLE_ENDIAN);
            readFully(in, fb);
            fb.flip();
            FileInfo fi = new FileInfo();
            fi.size = fb.getInt();
            fi.uoff = fb.getInt();
            fi.folder = fb.getShort();
            fb.getShort(); // date
            fb.getShort(); // time
            fb.getShort(); // attribs
            ByteArrayOutputStream nameBuf = new ByteArrayOutputStream();
            ByteBuffer one = ByteBuffer.allocate(1);
            while (true) {
                one.clear();
                readFully(in, one);
                one.flip();
                byte b = one.get();
                if (b == 0) break;
                nameBuf.write(b);
            }
            fi.name = new String(nameBuf.toByteArray(), StandardCharsets.UTF_8);
            infos[i] = fi;
        }

        // prepare output channels per folder
        Map<Integer, List<FileInfo>> filesPerFolder = new LinkedHashMap<>();
        for (FileInfo fi : infos) {
            filesPerFolder.computeIfAbsent((int) fi.folder, k -> new ArrayList<>()).add(fi);
        }
        for (List<FileInfo> list : filesPerFolder.values()) {
            list.sort(Comparator.comparingInt(f -> f.uoff));
        }

        ByteBuffer dataBuf = ByteBuffer.allocate(0xFFFF);
        for (int f = 0; f < cFolders; f++) {
            List<FileInfo> list = filesPerFolder.get(f);
            if (list == null) continue;
            Iterator<FileInfo> it = list.iterator();
            FileInfo current = it.next();
            Path out = outputDir.resolve(current.name);
            Files.createDirectories(out.getParent());
            WritableByteChannel fileOut = Files.newByteChannel(out, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.StandardOpenOption.WRITE);
            int writtenForCurrent = 0;

            for (int j = 0; j < folderCCfData[f]; j++) {
                ByteBuffer db = ByteBuffer.allocate(8);
                db.order(ByteOrder.LITTLE_ENDIAN);
                readFully(in, db);
                db.flip();
                int csum = db.getInt();
                int cbData = Short.toUnsignedInt(db.getShort());
                int cbUncomp = Short.toUnsignedInt(db.getShort());
                dataBuf.clear();
                if (dataBuf.capacity() < cbData) {
                    dataBuf = ByteBuffer.allocate(cbData);
                }
                dataBuf.limit(cbData);
                readFully(in, dataBuf);
                dataBuf.flip();

                ByteBuffer checksumBuf = ByteBuffer.allocate(cbData + 4);
                checksumBuf.order(ByteOrder.LITTLE_ENDIAN);
                checksumBuf.putShort((short) cbData);
                checksumBuf.putShort((short) cbUncomp);
                checksumBuf.put(dataBuf.duplicate());
                checksumBuf.flip();
                int calc = ChecksumHelper.cabChecksum(checksumBuf);
                if (calc != csum) {
                    throw new IOException("CFDATA checksum mismatch");
                }

                ByteBuffer uncompressed;
                CfFolder.COMPRESS_TYPE comp = CfFolder.COMPRESS_TYPE.fromValue(Short.toUnsignedInt(folderType[f]));
                switch (comp) {
                    case TCOMP_TYPE_MSZIP:
                        if (dataBuf.remaining() < 2 || dataBuf.get() != 'C' || dataBuf.get() != 'K') {
                            throw new IOException("Invalid MSZIP signature");
                        }
                        byte[] compBytes = new byte[dataBuf.remaining()];
                        dataBuf.get(compBytes);
                        java.util.zip.Inflater inflater = new java.util.zip.Inflater(true);
                        inflater.setInput(compBytes);
                        byte[] outArr = new byte[cbUncomp];
                        try {
                            int written = inflater.inflate(outArr);
                            if (written < outArr.length) {
                                outArr = java.util.Arrays.copyOf(outArr, written);
                            }
                        } catch (java.util.zip.DataFormatException e) {
                            throw new IOException("MSZIP decompression failed", e);
                        } finally {
                            inflater.end();
                        }
                        uncompressed = ByteBuffer.wrap(outArr);
                        break;
                    case TCOMP_TYPE_LZX:
                    case TCOMP_TYPE_QUANTUM:
                        byte[] lzBytes = new byte[dataBuf.remaining()];
                        dataBuf.get(lzBytes);
                        java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(lzBytes);
                        byte[] lzOut = new byte[cbUncomp];
                        try (org.tukaani.xz.XZInputStream xz = new org.tukaani.xz.XZInputStream(bis)) {
                            int len = xz.read(lzOut);
                            if (len < lzOut.length) {
                                lzOut = java.util.Arrays.copyOf(lzOut, len);
                            }
                        }
                        uncompressed = ByteBuffer.wrap(lzOut);
                        break;
                    case TCOMP_TYPE_NONE:
                    default:
                        uncompressed = dataBuf.duplicate();
                        break;
                }

                while (uncompressed.hasRemaining()) {
                    int toWrite = Math.min(uncompressed.remaining(), current.size - writtenForCurrent);
                    ByteBuffer slice = uncompressed.slice();
                    slice.limit(toWrite);
                    fileOut.write(slice);
                    uncompressed.position(uncompressed.position() + toWrite);
                    writtenForCurrent += toWrite;
                    if (writtenForCurrent >= current.size && it.hasNext()) {
                        fileOut.close();
                        current = it.next();
                        out = outputDir.resolve(current.name);
                        Files.createDirectories(out.getParent());
                        fileOut = Files.newByteChannel(out, java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                                java.nio.file.StandardOpenOption.WRITE);
                        writtenForCurrent = 0;
                    }
                }
            }
            fileOut.close();
        }
    }

    private static void readFully(ReadableByteChannel ch, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            if (ch.read(buf) < 0) {
                throw new IOException("Unexpected end of stream");
            }
        }
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
