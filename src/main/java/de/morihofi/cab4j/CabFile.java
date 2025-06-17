package de.morihofi.cab4j;

import de.morihofi.cab4j.file.FileUtils;
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
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class CabFile {

    private static class FileEntry {
        ByteBuffer data;
        short attribs;
        short folder;
        java.time.LocalDateTime lastModified;

        FileEntry(ByteBuffer data, short attribs, short folder, java.time.LocalDateTime ts) {
            this.data = data;
            this.attribs = attribs;
            this.folder = folder;
            this.lastModified = ts;
        }
    }

    private final Map<String, FileEntry> files = new LinkedHashMap<>();
    private boolean enableChecksum = true;
    private Short cabinetSetId = null;
    private short cabinetIndex = 0;
    private CfFolder.COMPRESS_TYPE compressionType = CfFolder.COMPRESS_TYPE.TCOMP_TYPE_NONE;

    /**
     * Maximum amount of files allowed in a single cabinet file as specified in
     * the MS-CAB documentation.
     */
    public static final int MAX_FILES = 0xFFFF;

    /**
     * Maximum uncompressed size of an input file (in bytes) that can be stored
     * in a cabinet file. This value originates from the official specification
     * and represents the largest value that fits into the 32‑bit file size
     * fields.
     */
    public static final int MAX_FILE_SIZE = 0x7FFF8000;

    /**
     * Logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


    public void addFile(String filename, ByteBuffer bytes) {
        addFile(filename, bytes, (short) 0, (short) 0, java.time.LocalDateTime.now());
    }

    public void addFile(String filename, ByteBuffer bytes, short attribs) {
        addFile(filename, bytes, attribs, (short) 0, java.time.LocalDateTime.now());
    }

    public void addFile(String filename, ByteBuffer bytes, short attribs, short folder) {
        addFile(filename, bytes, attribs, folder, java.time.LocalDateTime.now());
    }

    public void addFile(String filename, ByteBuffer bytes, short attribs, short folder, java.time.LocalDateTime timestamp) {

        // Check if we can add files
        if (files.size() >= MAX_FILES) {
            throw new IllegalArgumentException("CAB File limit reached");
        }

        // Check if file bytes are too big -> can end in buffer overflow
        if (bytes.remaining() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "Byte size for file \"" + filename + "\" is too large (" + bytes.remaining()
                            + " bytes). Max allowed size is " + MAX_FILE_SIZE + " bytes");
        }

        files.put(filename, new FileEntry(bytes, attribs, folder, timestamp));
    }

    public void addFile(String filename, byte[] bytes) {
        addFile(filename, ByteBuffer.wrap(bytes), (short) 0, (short) 0, java.time.LocalDateTime.now());
    }

    /**
     * Adds a file from the given path to the cabinet.
     *
     * @param filename file name inside the cabinet
     * @param path     path to read the file contents from
     * @throws IOException              if reading the file fails
     * @throws IllegalArgumentException if the file limit is reached or the file
     *                                  size exceeds {@link Short#MAX_VALUE}
     */
    public void addFile(String filename, Path path) throws IOException {
        ByteBuffer data = FileUtils.readFile(path);
        short attribs = 0;
        try {
            DosFileAttributes dos = Files.readAttributes(path, DosFileAttributes.class);
            if (dos.isReadOnly()) attribs |= CfFile.ATTRIB_READONLY;
            if (dos.isHidden()) attribs |= CfFile.ATTRIB_HIDDEN;
            if (dos.isSystem()) attribs |= CfFile.ATTRIB_SYSTEM;
            if (dos.isArchive()) attribs |= CfFile.ATTRIB_ARCHIVE;
        } catch (UnsupportedOperationException ignored) {
            // DOS attributes not supported on this platform
        }
        FileTime ft = Files.getLastModifiedTime(path);
        java.time.LocalDateTime ts = java.time.LocalDateTime.ofInstant(ft.toInstant(), java.time.ZoneId.systemDefault());
        addFile(filename, data, attribs, (short) 0, ts);
    }

    /**
     * Recursively adds all files from the given directory. The path inside the cabinet
     * mirrors the relative path to the supplied directory.
     *
     * @param directory directory to read files from
     * @throws IOException if file reading fails
     */
    public void addDirectory(Path directory) throws IOException {
        Files.walk(directory)
                .filter(Files::isRegularFile)
                .forEach(p -> {
                    Path rel = directory.relativize(p);
                    String name = rel.toString().replace('\\', '/');
                    try {
                        addFile(name, p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private ByteBuffer buildCabinet(boolean incrementIndex) throws IOException {

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

        for (Map.Entry<String, FileEntry> entry : files.entrySet()) {
            String fileName = entry.getKey();
            FileEntry fileEntry = entry.getValue();
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
        List<CfData> dataDefs = new ArrayList<>();
        List<ByteBuffer> dataBlocks = new ArrayList<>();

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

            ByteBuffer dataBlock;
            if (compressionType == CfFolder.COMPRESS_TYPE.TCOMP_TYPE_MSZIP) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                bos.write('C');
                bos.write('K');
                Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
                try (DeflaterOutputStream dos = new DeflaterOutputStream(bos, def)) {
                    byte[] arr = new byte[folderBuf.remaining()];
                    folderBuf.duplicate().get(arr);
                    dos.write(arr);
                }
                byte[] comp = bos.toByteArray();
                dataBlock = ByteBuffer.wrap(comp);
            } else {
                dataBlock = folderBuf.duplicate();
            }

            CfData cfData = new CfData();
            cfData.setCbUncomp((short) folderBuf.remaining());
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

            CfFolder cfFolder = new CfFolder();
            cfFolder.setTypeCompress(compressionType);
            cfFolder.setcCfData((short) 1);

            folderDefs.add(cfFolder);
            dataDefs.add(cfData);
            dataBlocks.add(dataBlock);
        }

        int coffFiles = cfHeader.getByteSize() + folderDefs.size() * folderDefs.get(0).getByteSize();
        cfHeader.setCoffFiles(coffFiles);

        int offset = coffFiles + cfFileOffsets;
        for (int i = 0; i < folderCount; i++) {
            CfFolder f = folderDefs.get(i);
            f.setCoffCabStart(offset);
            offset += dataDefs.get(i).getByteSize() + dataBlocks.get(i).remaining();
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
            cabinetBuffer.put(dataDefs.get(i).build());
            LOG.info("Adding {} bytes of {} data", dataBlocks.get(i).remaining(), compressionType);
            cabinetBuffer.put(dataBlocks.get(i).duplicate());
        }

        cabinetBuffer.flip();
        if (incrementIndex) {
            cabinetIndex++;
        }
        return cabinetBuffer;
    }

    public ByteBuffer createCabinet() throws IOException {
        return buildCabinet(true);
    }

    /**
     * Creates a set of cabinets if the resulting cabinet would exceed the provided size limit.
     * The files added to this {@code CabFile} are split across multiple cabinets.
     *
     * @param maxCabinetSize maximum size in bytes of a single cabinet
     * @return list of cabinet buffers in the same order they should be written
     * @throws IOException if cabinet creation fails
     */
    public List<ByteBuffer> createCabinetSet(long maxCabinetSize) throws IOException {
        List<ByteBuffer> result = new ArrayList<>();

        Map<String, FileEntry> pending = new LinkedHashMap<>(files);
        files.clear();

        while (!pending.isEmpty()) {
            files.clear();
            Iterator<Map.Entry<String, FileEntry>> it = pending.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, FileEntry> e = it.next();
                files.put(e.getKey(), e.getValue());
                ByteBuffer test = buildCabinet(false);
                if (test.remaining() > maxCabinetSize && files.size() > 1) {
                    files.remove(e.getKey());
                    break;
                }
                it.remove();
                if (test.remaining() > maxCabinetSize) {
                    break;
                }
            }

            ByteBuffer cab = buildCabinet(true);
            result.add(cab);
            files.clear();
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

    public static void main(String[] args) throws IOException {


        CabFile cabFile = new CabFile();
        cabFile.setEnableChecksum(true);

        cabFile.setCompressionType(CfFolder.COMPRESS_TYPE.TCOMP_TYPE_MSZIP);
        cabFile.addFile("hello.c", Paths.get("test/hello.c"));
        cabFile.addFile("welcome.c", Paths.get("test/welcome.c"));
        // cabFile.addFile("MS-CAB.pdf", Paths.get("docu/[MS-CAB].pdf"));

        ByteBuffer cabFileBuffer = cabFile.createCabinet();

        try (FileChannel fc = FileChannel.open(Paths.get("output.cab"), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            fc.write(cabFileBuffer);
        }

    }


}
