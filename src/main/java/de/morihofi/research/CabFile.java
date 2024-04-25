package de.morihofi.research;

import de.morihofi.research.structures.CfData;
import de.morihofi.research.structures.CfFile;
import de.morihofi.research.structures.CfFolder;
import de.morihofi.research.structures.CfHeader;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class CabFile {

    private List<String> filenames = new ArrayList<>();
    private List<byte[]> fileContents = new ArrayList<>();

    /**
     * Logger
     */
    //private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().getClass());

    private CfHeader header;
    private Vector<CfFolder> folders;
    private Vector<CfFile> files;
    private Vector<CfData> cfdata;
    private Vector<Byte> data;


    public void addFile(String filename, byte[] content) {
        this.filenames.add(filename);
        this.fileContents.add(content);
    }

    public void createCabinet() throws IOException {
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

        try (FileChannel fc = FileChannel.open(Paths.get("output.cab"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            fc.write(cabinetFile);
        }*/
    }

    public static void main(String[] args) throws IOException {


        CabFile cabFile = new CabFile();
        cabFile.addFile("hello.c", Files.readAllBytes(Paths.get("test/hello.c")));
        cabFile.addFile("welcome.c", Files.readAllBytes(Paths.get("test/welcome.c")));

        cabFile.createCabinet();
    }


}
