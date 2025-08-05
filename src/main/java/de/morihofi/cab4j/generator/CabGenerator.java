package de.morihofi.cab4j.generator;

import de.morihofi.cab4j.archive.CabArchive;
import de.morihofi.cab4j.structures.CfData;
import de.morihofi.cab4j.structures.CfFile;
import de.morihofi.cab4j.structures.CfFolder;
import de.morihofi.cab4j.structures.CfHeader;
import de.morihofi.cab4j.util.ChecksumHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.Deflater;

import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

/**
 * Generates CAB files from a {@link CabArchive} instance. The implementation
 * operates in a streaming manner using {@link ReadableByteChannel} and
 * {@link WritableByteChannel} so that large files do not need to be kept in
 * memory completely.
 */
public class CabGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final int CFDATA_MAX = 0xFFFF;

    private final CabArchive archive;
    private boolean enableChecksum = true;
    private Short cabinetSetId = null;
    private short cabinetIndex = 0;
    private CfFolder.COMPRESS_TYPE compressionType = CfFolder.COMPRESS_TYPE.TCOMP_TYPE_NONE;

    /**
     * Creates a new generator operating on the given archive.
     *
     * @param archive the source archive that supplies files for the cabinet
     */
    public CabGenerator(CabArchive archive) {
        this.archive = archive;
    }

    /**
     * Writes a cabinet containing all files of the underlying archive to the
     * provided {@link WritableByteChannel}.
     */
    public void writeCabinet(WritableByteChannel out) throws IOException {
        writeCabinet(archive.getFileEntries(), out, true);
    }

    private static class DataBlock {
        final CfData header;
        final ByteBuffer data;

        DataBlock(CfData h, ByteBuffer d) {
            this.header = h;
            this.data = d;
        }
    }

    private void writeCabinet(Map<String, CabArchive.FileEntry> files, WritableByteChannel out,
                              boolean incrementIndex) throws IOException {
        LOG.info("Creating cabinet of {} files", files.size());

        CfHeader header = new CfHeader();
        header.setCFiles((short) files.size());
        if (cabinetSetId == null) {
            cabinetSetId = (short) ThreadLocalRandom.current().nextInt(0x10000);
        }
        header.setSetID(cabinetSetId);
        header.setiCabinet(cabinetIndex);

        List<CfFile> cfFiles = new ArrayList<>();
        Map<Integer, Integer> folderDataBlocks = new HashMap<>();
        Map<Integer, List<DataBlock>> folderBlocks = new HashMap<>();
        Map<Integer, Integer> folderOffsets = new HashMap<>();
        Map<Integer, Long> folderCompressedSizes = new HashMap<>();
        int maxFolder = 0;
        int cfFileSectionSize = 0;

        int chunkLimit = compressionType == CfFolder.COMPRESS_TYPE.TCOMP_TYPE_NONE ? CFDATA_MAX : 0x8000;

        for (Map.Entry<String, CabArchive.FileEntry> e : files.entrySet()) {
            String name = e.getKey();
            CabArchive.FileEntry fe = e.getValue();
            short folder = fe.folder;

            CfFile cfFile = new CfFile();
            cfFile.setCbFile((int) fe.size);
            cfFile.setiFolder(folder);
            cfFile.setDateTime(fe.lastModified);
            cfFile.setAttribs(fe.attribs);
            cfFile.setSzName(name.getBytes(StandardCharsets.UTF_8));
            int off = folderOffsets.getOrDefault((int) folder, 0);
            cfFile.setUoffFolderStart(off);
            folderOffsets.put((int) folder, off + (int) fe.size);
            cfFiles.add(cfFile);
            cfFileSectionSize += cfFile.getByteSize();

            List<DataBlock> blocks = folderBlocks.computeIfAbsent((int) folder, k -> new ArrayList<>());

            try (ReadableByteChannel ch = Channels.newChannel(fe.in)) {
                long remaining = fe.size;
                while (remaining > 0) {
                    int chunk = (int) Math.min(remaining, chunkLimit);
                    ByteBuffer raw = ByteBuffer.allocate(chunk);
                    readFully(ch, raw);
                    raw.flip();

                    ByteBuffer compBuf;
                    switch (compressionType) {
                        case TCOMP_TYPE_MSZIP:
                            byte[] outBytes = new byte[chunk + 256];
                            Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
                            def.setInput(raw.array(), raw.position(), raw.remaining());
                            def.finish();
                            int clen = def.deflate(outBytes);
                            def.end();
                            ByteBuffer tmp = ByteBuffer.allocate(clen + 2);
                            tmp.put((byte) 'C');
                            tmp.put((byte) 'K');
                            tmp.put(outBytes, 0, clen);
                            tmp.flip();
                            compBuf = tmp;
                            break;
                        case TCOMP_TYPE_LZX:
                        case TCOMP_TYPE_QUANTUM:
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            try (XZOutputStream xz = new XZOutputStream(baos, new LZMA2Options())) {
                                xz.write(raw.array(), raw.position(), raw.remaining());
                            }
                            compBuf = ByteBuffer.wrap(baos.toByteArray());
                            break;
                        case TCOMP_TYPE_NONE:
                        default:
                            compBuf = raw.duplicate();
                            break;
                    }

                    CfData cfData = new CfData();
                    cfData.setCbData((short) compBuf.remaining());
                    cfData.setCbUncomp((short) chunk);

                    if (enableChecksum) {
                        ByteBuffer checksumBuffer = ByteBuffer.allocate(compBuf.remaining() + 4);
                        checksumBuffer.order(ByteOrder.LITTLE_ENDIAN);
                        checksumBuffer.putShort((short) compBuf.remaining());
                        checksumBuffer.putShort((short) chunk);
                        checksumBuffer.put(compBuf.duplicate());
                        checksumBuffer.flip();
                        cfData.setCsum(ChecksumHelper.cabChecksum(checksumBuffer));
                    } else {
                        cfData.setCsum(0);
                    }

                    blocks.add(new DataBlock(cfData, compBuf.duplicate()));
                    folderCompressedSizes.put((int) folder,
                            folderCompressedSizes.getOrDefault((int) folder, 0L)
                                    + cfData.getByteSize() + compBuf.remaining());
                    folderDataBlocks.put((int) folder,
                            folderDataBlocks.getOrDefault((int) folder, 0) + 1);

                    remaining -= chunk;
                }
            }

            if (folder > maxFolder) maxFolder = folder;
        }

        int folderCount = maxFolder + 1;
        header.setCFolders((short) folderCount);

        List<CfFolder> folderDefs = new ArrayList<>();
        int coffFiles = header.getByteSize() + folderCount * new CfFolder().getByteSize();
        header.setCoffFiles(coffFiles);

        int dataOffset = coffFiles + cfFileSectionSize;
        for (int i = 0; i < folderCount; i++) {
            long compSize = folderCompressedSizes.getOrDefault(i, 0L);
            int blocks = folderDataBlocks.getOrDefault(i, 0);
            CfFolder folder = new CfFolder();
            folder.setTypeCompress(compressionType);
            folder.setcCfData((short) blocks);
            folder.setCoffCabStart(dataOffset);
            dataOffset += (int) compSize;
            folderDefs.add(folder);
        }
        header.setCbCabinet(dataOffset);

        out.write(header.build());
        for (CfFolder f : folderDefs) {
            out.write(f.build());
        }
        for (CfFile f : cfFiles) {
            out.write(f.build());
        }

        for (int i = 0; i < folderCount; i++) {
            List<DataBlock> list = folderBlocks.get(i);
            if (list == null) continue;
            for (DataBlock db : list) {
                out.write(db.header.build());
                out.write(db.data.duplicate());
            }
        }

        if (incrementIndex) {
            cabinetIndex++;
        }
    }

    private static void readFully(ReadableByteChannel ch, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            if (ch.read(buf) < 0) {
                throw new IOException("Unexpected end of stream");
            }
        }
    }

    /**
     * Convenience method returning the generated cabinet as a {@link ByteBuffer}.
     * The underlying input streams are consumed and the entire cabinet is kept in
     * memory.
     */
    public ByteBuffer createCabinet() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (WritableByteChannel ch = Channels.newChannel(bos)) {
            writeCabinet(ch);
        }
        return ByteBuffer.wrap(bos.toByteArray());
    }

    /**
     * Returns whether CFDATA checksums are written.
     */
    public boolean isEnableChecksum() {
        return enableChecksum;
    }

    /**
     * Enables or disables writing CFDATA checksums.
     *
     * @param enableChecksum {@code true} to generate checksums
     */
    public void setEnableChecksum(boolean enableChecksum) {
        this.enableChecksum = enableChecksum;
    }

    /**
     * Returns the compression type used for data blocks.
     */
    public CfFolder.COMPRESS_TYPE getCompressionType() {
        return compressionType;
    }

    /**
     * Sets the compression type used for data blocks.
     *
     * @param compressionType the compression algorithm to apply
     */
    public void setCompressionType(CfFolder.COMPRESS_TYPE compressionType) {
        this.compressionType = compressionType;
    }

    /**
     * Starts a new cabinet set by resetting the set ID and cabinet index.
     */
    public void resetCabinetSet() {
        this.cabinetSetId = null;
        this.cabinetIndex = 0;
    }
}

