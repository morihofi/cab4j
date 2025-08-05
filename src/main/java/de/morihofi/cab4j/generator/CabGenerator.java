package de.morihofi.cab4j.generator;

import de.morihofi.cab4j.archive.CabArchive;
import de.morihofi.cab4j.structures.CfData;
import de.morihofi.cab4j.structures.CfFile;
import de.morihofi.cab4j.structures.CfFolder;
import de.morihofi.cab4j.structures.CfHeader;
import de.morihofi.cab4j.util.ChecksumHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Generates CAB files from a {@link CabArchive} instance.
 */
public class CabGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final CabArchive archive;
    private boolean enableChecksum = true;
    private Short cabinetSetId = null;
    private short cabinetIndex = 0;
    private CfFolder.COMPRESS_TYPE compressionType = CfFolder.COMPRESS_TYPE.TCOMP_TYPE_NONE;

    public CabGenerator(CabArchive archive) {
        this.archive = archive;
    }

    private ByteBuffer buildCabinet(Map<String, CabArchive.FileEntry> files, boolean incrementIndex) throws IOException {
        LOG.info("Creating cabinet of {} files", files.size());

        CfHeader cfHeader = new CfHeader();
        cfHeader.setCFiles((short) files.size());
        if (cabinetSetId == null) {
            cabinetSetId = (short) ThreadLocalRandom.current().nextInt(0x10000);
        }
        cfHeader.setSetID(cabinetSetId);
        cfHeader.setiCabinet(cabinetIndex);

        List<CfFile> cfFileDefinitions = new ArrayList<>();
        int cfFileOffsets = 0;
        Map<Integer, Integer> folderOffsets = new HashMap<>();
        Map<Integer, Integer> folderSizes = new HashMap<>();
        Map<Integer, List<ByteBuffer>> folderData = new HashMap<>();
        int maxFolder = 0;

        for (Map.Entry<String, CabArchive.FileEntry> entry : files.entrySet()) {
            String fileName = entry.getKey();
            CabArchive.FileEntry fileEntry = entry.getValue();
            ByteBuffer fileByte = fileEntry.data;
            short folder = fileEntry.folder;

            LOG.info("Creating CFFile entry for file {} with file contents of {} byte", fileName, fileByte.remaining());

            CfFile cfFile = new CfFile();
            cfFile.setCbFile(fileByte.remaining());
            cfFile.setiFolder(folder);
            cfFile.setDateTime(fileEntry.lastModified);
            cfFile.setAttribs(fileEntry.attribs);
            cfFile.setSzName(fileName.getBytes(StandardCharsets.UTF_8));

            int off = folderOffsets.getOrDefault((int) folder, 0);
            cfFile.setUoffFolderStart(off);
            folderOffsets.put((int) folder, off + fileByte.remaining());

            cfFileDefinitions.add(cfFile);
            cfFileOffsets += cfFile.getByteSize();

            folderSizes.put((int) folder, folderSizes.getOrDefault((int) folder, 0) + fileByte.remaining());
            folderData.computeIfAbsent((int) folder, k -> new ArrayList<>()).add(fileByte.duplicate());
            if (folder > maxFolder) maxFolder = folder;
        }

        int folderCount = maxFolder + 1;
        cfHeader.setCFolders((short) folderCount);

        List<CfFolder> folderDefs = new ArrayList<>();
        List<List<CfData>> dataDefsPerFolder = new ArrayList<>();
        List<List<ByteBuffer>> dataBlocksPerFolder = new ArrayList<>();

        for (int i = 0; i < folderCount; i++) {
            int uncompSize = folderSizes.getOrDefault(i, 0);
            ByteBuffer folderBuf = ByteBuffer.allocate(uncompSize);
            List<ByteBuffer> pieces = folderData.get(i);
            if (pieces != null) {
                for (ByteBuffer bb : pieces) {
                    ByteBuffer dup = bb.duplicate();
                    dup.position(0);
                    folderBuf.put(dup);
                }
            }
            folderBuf.flip();

            List<CfData> folderCfData = new ArrayList<>();
            List<ByteBuffer> folderBlocks = new ArrayList<>();

            while (folderBuf.hasRemaining()) {
                int chunkSize = Math.min(folderBuf.remaining(), 0xFFFF);
                ByteBuffer chunk = folderBuf.slice();
                chunk.limit(chunkSize);

                ByteBuffer dataBlock;
                if (compressionType == CfFolder.COMPRESS_TYPE.TCOMP_TYPE_MSZIP) {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    bos.write('C');
                    bos.write('K');
                    Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
                    try (DeflaterOutputStream dos = new DeflaterOutputStream(bos, def)) {
                        byte[] arr = new byte[chunk.remaining()];
                        chunk.duplicate().get(arr);
                        dos.write(arr);
                    }
                    byte[] comp = bos.toByteArray();
                    dataBlock = ByteBuffer.wrap(comp);
                } else if (compressionType == CfFolder.COMPRESS_TYPE.TCOMP_TYPE_LZX ||
                        compressionType == CfFolder.COMPRESS_TYPE.TCOMP_TYPE_QUANTUM) {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] arr = new byte[chunk.remaining()];
                    chunk.duplicate().get(arr);
                    org.tukaani.xz.LZMA2Options opts = new org.tukaani.xz.LZMA2Options();
                    opts.setDictSize(1 << 16);
                    try (org.tukaani.xz.XZOutputStream xzOut = new org.tukaani.xz.XZOutputStream(bos, opts)) {
                        xzOut.write(arr);
                    }
                    byte[] comp = bos.toByteArray();
                    dataBlock = ByteBuffer.wrap(comp);
                } else {
                    dataBlock = chunk.duplicate();
                }

                CfData cfData = new CfData();
                cfData.setCbUncomp((short) chunk.remaining());
                cfData.setCbData((short) dataBlock.remaining());

                if (enableChecksum) {
                    ByteBuffer checksumBuffer = ByteBuffer.allocate(Short.toUnsignedInt(cfData.getCbData()) + 4);
                    checksumBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    checksumBuffer.putShort(cfData.getCbData());
                    checksumBuffer.putShort(cfData.getCbUncomp());
                    checksumBuffer.put(dataBlock.duplicate());
                    checksumBuffer.flip();
                    cfData.setCsum(ChecksumHelper.cabChecksum(checksumBuffer));
                } else {
                    cfData.setCsum(0);
                }

                folderCfData.add(cfData);
                folderBlocks.add(dataBlock);

                folderBuf.position(folderBuf.position() + chunkSize);
            }

            CfFolder cfFolder = new CfFolder();
            cfFolder.setTypeCompress(compressionType);
            cfFolder.setcCfData((short) folderCfData.size());

            folderDefs.add(cfFolder);
            dataDefsPerFolder.add(folderCfData);
            dataBlocksPerFolder.add(folderBlocks);
        }

        int coffFiles = cfHeader.getByteSize() + folderDefs.size() * folderDefs.get(0).getByteSize();
        cfHeader.setCoffFiles(coffFiles);

        int offset = coffFiles + cfFileOffsets;
        for (int i = 0; i < folderCount; i++) {
            CfFolder f = folderDefs.get(i);
            f.setCoffCabStart(offset);
            List<CfData> defs = dataDefsPerFolder.get(i);
            List<ByteBuffer> blocks = dataBlocksPerFolder.get(i);
            for (int j = 0; j < defs.size(); j++) {
                offset += defs.get(j).getByteSize() + blocks.get(j).remaining();
            }
        }
        cfHeader.setCbCabinet(offset);

        ByteBuffer cabinetBuffer = ByteBuffer.allocate(cfHeader.getCbCabinet());
        cabinetBuffer.order(ByteOrder.LITTLE_ENDIAN);
        cabinetBuffer.put(cfHeader.build());
        for (CfFolder f : folderDefs) {
            cabinetBuffer.put(f.build());
        }
        for (CfFile cfFile : cfFileDefinitions) {
            cabinetBuffer.put(cfFile.build());
        }
        for (int i = 0; i < folderCount; i++) {
            List<CfData> defs = dataDefsPerFolder.get(i);
            List<ByteBuffer> blocks = dataBlocksPerFolder.get(i);
            for (int j = 0; j < defs.size(); j++) {
                cabinetBuffer.put(defs.get(j).build());
                LOG.info("Adding {} bytes of {} data", blocks.get(j).remaining(), compressionType);
                cabinetBuffer.put(blocks.get(j).duplicate());
            }
        }

        cabinetBuffer.flip();
        if (incrementIndex) {
            cabinetIndex++;
        }
        return cabinetBuffer;
    }

    private ByteBuffer buildCabinet(boolean incrementIndex) throws IOException {
        return buildCabinet(archive.getFileEntries(), incrementIndex);
    }

    /**
     * Generate a single cabinet file.
     */
    public ByteBuffer createCabinet() throws IOException {
        return buildCabinet(true);
    }

    /**
     * Creates a set of cabinets if the resulting cabinet would exceed the provided size limit.
     * The files contained in the supplied archive are split across multiple cabinets.
     */
    public List<ByteBuffer> createCabinetSet(long maxCabinetSize) throws IOException {
        List<ByteBuffer> result = new ArrayList<>();

        Map<String, CabArchive.FileEntry> pending = new LinkedHashMap<>(archive.getFileEntries());

        while (!pending.isEmpty()) {
            Map<String, CabArchive.FileEntry> part = new LinkedHashMap<>();
            Iterator<Map.Entry<String, CabArchive.FileEntry>> it = pending.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, CabArchive.FileEntry> e = it.next();
                part.put(e.getKey(), e.getValue());
                ByteBuffer test = buildCabinet(part, false);
                if (test.remaining() > maxCabinetSize && part.size() > 1) {
                    part.remove(e.getKey());
                    break;
                }
                it.remove();
                if (test.remaining() > maxCabinetSize) {
                    break;
                }
            }
            ByteBuffer cab = buildCabinet(part, true);
            result.add(cab);
        }

        return result;
    }

    public boolean isEnableChecksum() {
        return enableChecksum;
    }

    public void setEnableChecksum(boolean enableChecksum) {
        this.enableChecksum = enableChecksum;
    }

    public CfFolder.COMPRESS_TYPE getCompressionType() {
        return compressionType;
    }

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
