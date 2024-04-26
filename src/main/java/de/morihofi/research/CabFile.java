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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class CabFile {

    private final Map<String, byte[]> files = new LinkedHashMap<>();
    private boolean enableChecksum = false;

    /**
     * Logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().getClass());


    public void addFile(String filename, byte[] bytes) {

        //Check if we can add files
        if (files.size() > (int) 0xFFFF) {
            throw new IllegalArgumentException("CAB File limit reached");
        }

        //Check if file bytes are too big -> can end in buffer overflow
        if (bytes.length > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Byte size for file \"" + filename + "\" is too large (" + bytes.length + " bytes). Max allowed size is " + Short.MAX_VALUE + " bytes");
        }

        files.put(filename, bytes);
    }

    private void addFile(String filename, Path path) throws IOException {
        addFile(filename, Files.readAllBytes(path));
    }

    public ByteBuffer createCabinet() throws IOException {

        LOG.info("Creating cabinet of {} files", files.size());

        CfHeader cfHeader = new CfHeader();
        cfHeader.setCFolders((short) 1); //Number of folders
        cfHeader.setCFiles((short) files.size()); //Number of files


        CfFolder cfFolder = new CfFolder();


        List<CfFile> cfFileDefinitions = new ArrayList<>();
        int cfFileOffsets = 0;
        int cfFileSizeUncompressed = 0;
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {

            String fileName = entry.getKey();
            byte[] fileByte = entry.getValue();

            LOG.info("Creating CFFile entry for file {} with file contents of {} byte", fileName, fileByte.length);

            CfFile cfFile = new CfFile();
            cfFile.setCbFile(fileByte.length); //Specifies the uncompressed size of this file, in bytes
            cfFile.setiFolder((short) 0);
            cfFile.setDate((short) 0); //Date of this file, in the format ((year–1980) << 9)+(month << 5)+(day), where month={1..12} and day={1..31}. This "date" is typically considered the "last modified" date in local time, but the actual definition is application-defined
            cfFile.setTime((short) 0); //Time of this file, in the format (hour << 11)+(minute << 5)+(seconds/2), where hour={0..23}. This "time" is typically considered the "last modified" time in local time, but the actual definition is application-defined.
            cfFile.setAttribs((short) 0); //Attributes of this file
            cfFile.setSzName(fileName.getBytes(StandardCharsets.UTF_8)); //Filename

            if (cfFileOffsets > 0) {
                cfFile.setUoffFolderStart(cfHeader.getByteSize() + cfFolder.getByteSize() + cfFileOffsets + 9);
            }

            cfFileOffsets += cfFile.getByteSize();
            cfFileSizeUncompressed += cfFile.getCbFile();

            cfFileDefinitions.add(cfFile);
        }

        CfData cfData = new CfData();
        cfData.setCbUncomp((short) cfFileSizeUncompressed); //Uncompressed size
        cfData.setCbData((short) cfFileSizeUncompressed); //Compressed size
        cfData.setCsum(calculateCsum(cfFileSizeUncompressed)); //Checksum

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


        for (byte[] fileByteBuffer : files.values()) {
            LOG.info("Adding file, cab buffer position before: {}", cabinetBuffer.position());
            LOG.info("File size: {} bytes", fileByteBuffer.length);

            cabinetBuffer.put(fileByteBuffer);

            LOG.info("Cab-Buffer position after: {}", cabinetBuffer.position());
        }

        LOG.info("Flipping buffer");
        cabinetBuffer.flip(); // Prepare to read from the buffer

        return cabinetBuffer;
    }

    private int calculateCsum(int cfFileSizeUncompressed) {
        /* This is currently not implemented */
        return 0;
    }

    public boolean isEnableChecksum() {
        return enableChecksum;
    }

    public void setEnableChecksum(boolean enableChecksum) {
        this.enableChecksum = enableChecksum;
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
