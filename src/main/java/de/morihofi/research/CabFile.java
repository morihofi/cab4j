package de.morihofi.research;

import de.morihofi.research.file.FileUtils;
import de.morihofi.research.structures.CfData;
import de.morihofi.research.structures.CfFile;
import de.morihofi.research.structures.CfFolder;
import de.morihofi.research.structures.CfHeader;
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

    private Map<String, byte[]> files = new LinkedHashMap<>();


    /**
     * Logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().getClass());


    public void addFile(String filename, byte[] byteBuffer) {
        files.put(filename, byteBuffer);
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
        cfFolder.setTypeCompress(CfFolder.COMPRESS_TYPE.TCOMP_TYPE_NONE);
        cfFolder.setcCfData((short) 1); //Specifies the number of CFDATA structures for this folder that are actually in this cabinet

        List<CfFile> cfFileDefinitions = new ArrayList<>();
        int cfFileOffsets = 0;
        int cfFileSizeUncompressed = 0;
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {

            String fileName = entry.getKey();
            byte[] fileByte = entry.getValue();

            LOG.info("Creating CFFile entry for file {} with file contents of {} byte", fileName, fileByte.length );

            CfFile cfFile = new CfFile();
            cfFile.setCbFile(fileByte.length); //Specifies the uncompressed size of this file, in bytes
            cfFile.setAttribs((short) 0); //Attributes of this file
            cfFile.setDate((short) 0); //Date of this file, in the format ((year–1980) << 9)+(month << 5)+(day), where month={1..12} and day={1..31}. This "date" is typically considered the "last modified" date in local time, but the actual definition is application-defined
            cfFile.setTime((short) 0); //Time of this file, in the format (hour << 11)+(minute << 5)+(seconds/2), where hour={0..23}. This "time" is typically considered the "last modified" time in local time, but the actual definition is application-defined.
            cfFile.setSzName(fileName.getBytes(StandardCharsets.UTF_8)); //Filename

            cfFileOffsets += cfFile.getByteSize();
            cfFileSizeUncompressed += cfFile.getCbFile();

            cfFileDefinitions.add(cfFile);
        }

        CfData cfData = new CfData();
        cfData.setCbUncomp((short) cfFileSizeUncompressed); //Uncompressed size
        cfData.setCbData((short) cfFileSizeUncompressed); //Compressed size


        // Adjust header
        cfHeader.setCoffFiles(cfHeader.getByteSize() + cfFolder.getByteSize()); //Specifies the absolute file offset, in bytes, of the first CFFILE field entry (Size of the Header and Folder definitions)
        cfHeader.setCbCabinet(cfHeader.getCoffFiles() + cfFileOffsets + cfData.getCbData() + cfData.getByteSize()); //Total file size incl. headers
        cfFolder.setCoffCabStart(cfHeader.getCoffFiles() + cfFileOffsets + cfData.getCbData()); // Specifies the absolute file offset of the first CFDATA field block for the folder.

        ByteBuffer cabinetBuffer = ByteBuffer.allocate(cfHeader.getCbCabinet());
        cabinetBuffer.order(ByteOrder.LITTLE_ENDIAN);
        cabinetBuffer.put(cfHeader.build());
        cabinetBuffer.put(cfFolder.build());
        for (CfFile cfFile : cfFileDefinitions) {
            cabinetBuffer.put(cfFile.build());
        }
        for (byte[] fileByteBuffer : files.values()) {
            LOG.info("Adding file, cab buffer position before: {}", cabinetBuffer.position());
            LOG.info("File size: {} bytes", fileByteBuffer.length);

            cabinetBuffer.put(fileByteBuffer);

            LOG.info("Cab-Buffer position after: {}", cabinetBuffer.position());
        }
        HexView.hexView(cabinetBuffer);
        cabinetBuffer.put(cfData.build());



        /*header = new CFHeader(); //initialise header
        cfdata = new Vector<>();
        Vector<Byte> alldata = new Vector<>();
        for (byte[] b : fileContents) {
            for (byte value : b) {
                alldata.add(value);
            }
        }
        //alldata is a vector of bytes containing all bytes to be saved
        ArrayList<Vector<Byte>> dataSet = new ArrayList<>();

        while (!alldata.isEmpty()) {
            Vector<Byte> tempBytes = new Vector<>();
            for (int i = 0; i < 0x8000 && i < alldata.size(); i++) {
                tempBytes.add(alldata.get(i));
            }
            dataSet.add(tempBytes);
            if (alldata.size() >= 0x8000) {
                alldata.subList(0, 0x8000).clear();
            } else {
                alldata.clear();
            }
        }

        for (Vector<Byte> b : dataSet) {
            cfdata.add(new CfData(b));
        }

        //make CFFiles
        CfFile tempFile;
        int offset = 0;
        files = new Vector<>();
        for (int i = 0; i < filenames.size(); i++) {
            tempFile = new CfFile(filenames.get(i), fileContents.get(i).length, offset, 0);
            offset = offset + fileContents.get(i).length;
            files.add(tempFile);
        }

        //make folders
        folders = new Vector<>();
        CfFolder tempFolder = new CfFolder();
        tempFolder.setcCfData((short) cfdata.size());
        folders.add(tempFolder);
        int dataBlockOffset = header.makeByteArray().size();
        int filesBlockOffset;
        for (CfFolder f : folders) {
            dataBlockOffset = dataBlockOffset + f.makeByteArray().size();
        }
        filesBlockOffset = dataBlockOffset;
        for (CfFile f : files) {
            dataBlockOffset = dataBlockOffset + f.makeByteArray().size();
        }
        folders.get(0).setCoffCabStart(dataBlockOffset);
        header.setCFolders((short) folders.size());
        header.setCFiles((short) files.size());
        header.setCoffFiles(filesBlockOffset);
        int cabFileSize = 0;
        for (CfData d : cfdata) {
            cabFileSize = cabFileSize + d.makeByteArray().size();
        }
        header.setCbCabinet(dataBlockOffset + cabFileSize);

        data = new Vector<>();
        data.addAll(header.makeByteArray());
        for (CfFolder f : folders) {
            data.addAll(f.makeByteArray());
        }
        for (CfFile f : files) {
            data.addAll(f.makeByteArray());
        }

        //Vector<Byte> temp = cfdata.makeByteArray();
        //for (Byte b : temp)
        //    System.out.print((char)b.intValue());

        for (CfData d : cfdata) {
            data.addAll(d.makeByteArray());
        }



        // Write to ByteBuffer for demo purposes (would write to file in real application)
        ByteBuffer cabinetFile = ByteBuffer.allocate(1024 * 1024); // Adjust size as needed
        cabinetFile.put(headerBuffer);
        cabinetFile.put(folderBuffer);
        dataBlocks.forEach(cabinetFile::put);

        cabinetFile.flip(); // Prepare to read from the buffer
        // Here you would write the contents of `cabinetFile` to an actual file

        */
        cabinetBuffer.flip(); // Prepare to read from the buffer

        return cabinetBuffer;
    }

    public static void main(String[] args) throws IOException {


        CabFile cabFile = new CabFile();
        cabFile.addFile("hello.c", Paths.get("test/hello.c"));
        cabFile.addFile("welcome.c", Paths.get("test/welcome.c"));

        ByteBuffer cabFileBuffer = cabFile.createCabinet();

        try (FileChannel fc = FileChannel.open(Paths.get("output.cab"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            fc.write(cabFileBuffer);
        }

    }


}
