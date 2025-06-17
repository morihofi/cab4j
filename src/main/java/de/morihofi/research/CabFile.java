package de.morihofi.research;

import de.morihofi.research.file.FileUtils;
import de.morihofi.research.structures.CfData;
import de.morihofi.research.structures.CfFile;
import de.morihofi.research.structures.CfFolder;
import de.morihofi.research.structures.CfHeader;
import de.morihofi.research.util.ChecksumHelper;
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
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class CabFile {

    private static class FileEntry {
        ByteBuffer data;
        short attribs;

        FileEntry(ByteBuffer data, short attribs) {
            this.data = data;
            this.attribs = attribs;
        }
    }

    private final Map<String, FileEntry> files = new LinkedHashMap<>();
    private boolean enableChecksum = true;
    private Short cabinetSetId = null;
    private short cabinetIndex = 0;

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
        addFile(filename, bytes, (short) 0);
    }

    public void addFile(String filename, ByteBuffer bytes, short attribs) {

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

        files.put(filename, new FileEntry(bytes, attribs));
    }

    public void addFile(String filename, byte[] bytes) {
        addFile(filename, ByteBuffer.wrap(bytes), (short) 0);
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
        addFile(filename, data, attribs);
    }

    public ByteBuffer createCabinet() throws IOException {

        LOG.info("Creating cabinet of {} files", files.size());

        CfHeader cfHeader = new CfHeader();
        cfHeader.setCFolders((short) 1); //Number of folders
        cfHeader.setCFiles((short) files.size()); //Number of files
        if (cabinetSetId == null) {
            cabinetSetId = (short) ThreadLocalRandom.current().nextInt(0x10000);
        }
        cfHeader.setSetID(cabinetSetId);
        cfHeader.setiCabinet(cabinetIndex);


        CfFolder cfFolder = new CfFolder();


        List<CfFile> cfFileDefinitions = new ArrayList<>();
        int cfFileOffsets = 0;
        int cfFileSizeUncompressed = 0;
        for (Map.Entry<String, FileEntry> entry : files.entrySet()) {

            String fileName = entry.getKey();
            FileEntry fileEntry = entry.getValue();
            ByteBuffer fileByte = fileEntry.data;

            LOG.info("Creating CFFile entry for file {} with file contents of {} byte", fileName, fileByte.remaining());

            CfFile cfFile = new CfFile();
            cfFile.setCbFile(fileByte.remaining()); //Specifies the uncompressed size of this file, in bytes
            cfFile.setiFolder((short) 0);
            cfFile.setDate((short) 0); //Date of this file, in the format ((year–1980) << 9)+(month << 5)+(day), where month={1..12} and day={1..31}. This "date" is typically considered the "last modified" date in local time, but the actual definition is application-defined
            cfFile.setTime((short) 0); //Time of this file, in the format (hour << 11)+(minute << 5)+(seconds/2), where hour={0..23}. This "time" is typically considered the "last modified" time in local time, but the actual definition is application-defined.
            cfFile.setAttribs(fileEntry.attribs); //Attributes of this file
            cfFile.setSzName(fileName.getBytes(StandardCharsets.UTF_8)); //Filename

            // The uoffFolderStart value represents the uncompressed offset of
            // the file's data inside the folder
            cfFile.setUoffFolderStart(cfFileSizeUncompressed);

            cfFileOffsets += cfFile.getByteSize();
            cfFileSizeUncompressed += cfFile.getCbFile();

            cfFileDefinitions.add(cfFile);
        }

        CfData cfData = new CfData();
        cfData.setCbUncomp((short) cfFileSizeUncompressed); //Uncompressed size
        cfData.setCbData((short) cfFileSizeUncompressed); //Compressed size
        cfData.setCsum(calculateCsum()); //Checksum

        cfFolder.setTypeCompress(CfFolder.COMPRESS_TYPE.TCOMP_TYPE_NONE);
        cfFolder.setcCfData((short) 1); //Specifies the number of CFDATA structures for this folder that are actually in this cabinet

        // Adjust header
        cfHeader.setCoffFiles(cfHeader.getByteSize() + cfFolder.getByteSize()); //Specifies the absolute file offset, in bytes, of the first CFFILE field entry (Size of the Header and Folder definitions)
        cfHeader.setCbCabinet(cfHeader.getCoffFiles() + cfFileOffsets + cfData.getCbData() + cfData.getByteSize()); //Total file size incl. headers

        // Header was changed, so adjust folder
        cfFolder.setCoffCabStart(cfHeader.getByteSize() + cfFolder.getByteSize() + cfFileOffsets); // Specifies the absolute file offset of the first CFDATA field block for the folder.

        // Glue everything together
        ByteBuffer cabinetBuffer = ByteBuffer.allocate(cfHeader.getCbCabinet());
        cabinetBuffer.order(ByteOrder.LITTLE_ENDIAN);
        cabinetBuffer.put(cfHeader.build());
        cabinetBuffer.put(cfFolder.build());
        for (CfFile cfFile : cfFileDefinitions) {
            cabinetBuffer.put(cfFile.build());
        }
        cabinetBuffer.put(cfData.build());


        for (FileEntry fe : files.values()) {
            ByteBuffer fileByteBuffer = fe.data;
            LOG.info("Adding file, cab buffer position before: {}", cabinetBuffer.position());
            LOG.info("File size: {} bytes", fileByteBuffer.remaining());

            ByteBuffer dup = fileByteBuffer.duplicate();
            dup.position(0);
            cabinetBuffer.put(dup);

            LOG.info("Cab-Buffer position after: {}", cabinetBuffer.position());
        }

        LOG.info("Flipping buffer");
        cabinetBuffer.flip(); // Prepare to read from the buffer

        cabinetIndex++;

        return cabinetBuffer;
    }

    private int calculateCsum() {
        if (!enableChecksum) {
            return 0;
        }

        int cbData = 0;
        for (FileEntry fe : files.values()) {
            cbData += fe.data.remaining();
        }

        ByteBuffer checksumBuffer = ByteBuffer.allocate(cbData + 4);
        checksumBuffer.order(ByteOrder.LITTLE_ENDIAN);
        checksumBuffer.putShort((short) cbData); // cbData
        checksumBuffer.putShort((short) cbData); // cbUncomp

        for (FileEntry fe : files.values()) {
            ByteBuffer dup = fe.data.duplicate();
            dup.position(0);
            checksumBuffer.put(dup);
        }

        checksumBuffer.flip();
        return ChecksumHelper.cabChecksum(checksumBuffer);
    }

    public boolean isEnableChecksum() {
        return enableChecksum;
    }

    public void setEnableChecksum(boolean enableChecksum) {
        this.enableChecksum = enableChecksum;
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

        cabFile.addFile("hello.c", Paths.get("test/hello.c"));
        cabFile.addFile("welcome.c", Paths.get("test/welcome.c"));
        // cabFile.addFile("MS-CAB.pdf", Paths.get("docu/[MS-CAB].pdf"));

        ByteBuffer cabFileBuffer = cabFile.createCabinet();

        try (FileChannel fc = FileChannel.open(Paths.get("output.cab"), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            fc.write(cabFileBuffer);
        }

    }


}
