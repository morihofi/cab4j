package de.morihofi.cab4j;

import de.morihofi.cab4j.archive.CabArchive;
import de.morihofi.cab4j.generator.CabGenerator;
import de.morihofi.cab4j.structures.CfFile;
import de.morihofi.cab4j.structures.CfFolder;
import de.morihofi.cab4j.util.ChecksumHelper;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

public class CabAdvancedTest {

    private static class FileEntry {
        int size;
        int uoffFolderStart;
        short attribs;
        String name;
    }

    private static class ParsedCab {
        short cFolders;
        short cFiles;
        int[] folderCoffCabStart;
        short[] folderCCfData;
        short[] folderTypeCompress;
        int[] dataCsum;
        short[] dataCbData;
        short[] dataCbUncomp;
        FileEntry[] files;
    }

    private static ParsedCab parse(ByteBuffer cab) {
        ByteBuffer b = cab.duplicate();
        b.order(ByteOrder.LITTLE_ENDIAN);

        byte[] sig = new byte[4];
        b.get(sig);
        if (sig[0] != 'M' || sig[1] != 'S' || sig[2] != 'C' || sig[3] != 'F') {
            throw new IllegalArgumentException("not a CAB file");
        }
        b.getInt(); // reserved1
        b.getInt(); // cbCabinet
        b.getInt(); // reserved2
        int coffFiles = b.getInt();
        b.getInt(); // reserved3
        b.get(); // version minor
        b.get(); // version major
        short cFolders = b.getShort();
        short cFiles = b.getShort();
        b.getShort(); // flags
        b.getShort(); // setID
        b.getShort(); // iCabinet

        ParsedCab pc = new ParsedCab();
        pc.cFolders = cFolders;
        pc.cFiles = cFiles;
        pc.folderCoffCabStart = new int[cFolders];
        pc.folderCCfData = new short[cFolders];
        pc.folderTypeCompress = new short[cFolders];
        for (int i = 0; i < cFolders; i++) {
            pc.folderCoffCabStart[i] = b.getInt();
            pc.folderCCfData[i] = b.getShort();
            pc.folderTypeCompress[i] = b.getShort();
        }

        b.position(coffFiles);
        pc.files = new FileEntry[cFiles];
        for (int i = 0; i < cFiles; i++) {
            FileEntry fe = new FileEntry();
            fe.size = b.getInt();
            fe.uoffFolderStart = b.getInt();
            b.getShort(); // iFolder
            b.getShort(); // date
            b.getShort(); // time
            fe.attribs = b.getShort();
            StringBuilder sb = new StringBuilder();
            byte c;
            while ((c = b.get()) != 0) {
                sb.append((char) (c & 0xFF));
            }
            fe.name = sb.toString();
            pc.files[i] = fe;
        }

        pc.dataCsum = new int[cFolders];
        pc.dataCbData = new short[cFolders];
        pc.dataCbUncomp = new short[cFolders];
        for (int i = 0; i < cFolders; i++) {
            pc.dataCsum[i] = b.getInt();
            pc.dataCbData[i] = b.getShort();
            pc.dataCbUncomp[i] = b.getShort();
        }

        return pc;
    }

    private static ByteBuffer createSampleCab() throws Exception {
        ByteBuffer hello = ByteBuffer.wrap(TestData.HELLO_C);
        ByteBuffer welcome = ByteBuffer.wrap(TestData.WELCOME_C);

        CabArchive archive = new CabArchive();
        archive.addFile("hello.c", hello);
        archive.addFile("welcome.c", welcome);
        CabGenerator generator = new CabGenerator(archive);

        return generator.createCabinet();
    }

    private static ByteBuffer createAttribCab() throws Exception {
        ByteBuffer hello = ByteBuffer.wrap(TestData.HELLO_C);
        CabArchive archive = new CabArchive();
        archive.addFile("hello.c", hello, (short) (CfFile.ATTRIB_READONLY | CfFile.ATTRIB_HIDDEN));
        CabGenerator generator = new CabGenerator(archive);
        return generator.createCabinet();
    }

    @Test
    public void compression() throws Exception {
        ByteBuffer buf = createSampleCab();
        ParsedCab pc = parse(buf);
        assertEquals(1, pc.cFolders);
        assertEquals(CfFolder.COMPRESS_TYPE.TCOMP_TYPE_NONE.getValue(), pc.folderTypeCompress[0]);
        assertEquals(pc.dataCbData[0], pc.dataCbUncomp[0]);
    }

    @Test
    public void mszipCompression() throws Exception {
        ByteBuffer hello = ByteBuffer.wrap(TestData.HELLO_C);
        CabArchive archive2 = new CabArchive();
        archive2.addFile("hello.c", hello);
        CabGenerator generator2 = new CabGenerator(archive2);
        generator2.setCompressionType(CfFolder.COMPRESS_TYPE.TCOMP_TYPE_MSZIP);

        ByteBuffer buf = generator2.createCabinet();
        ParsedCab pc = parse(buf);
        assertEquals(CfFolder.COMPRESS_TYPE.TCOMP_TYPE_MSZIP.getValue(), pc.folderTypeCompress[0]);
    }

    @Test
    public void multiFolderCabinet() throws Exception {
        ByteBuffer hello = ByteBuffer.wrap(TestData.HELLO_C);
        ByteBuffer welcome = ByteBuffer.wrap(TestData.WELCOME_C);

        CabArchive archive3 = new CabArchive();
        archive3.addFile("hello.c", hello, (short) 0, (short) 0);
        archive3.addFile("welcome.c", welcome, (short) 0, (short) 1);
        CabGenerator generator3 = new CabGenerator(archive3);

        ByteBuffer buf = generator3.createCabinet();
        ParsedCab pc = parse(buf);
        assertEquals(2, pc.cFolders);
    }

    @Test
    public void attributePreservation() throws Exception {
        ByteBuffer buf = createSampleCab();
        ParsedCab pc = parse(buf);
        for (FileEntry fe : pc.files) {
            assertEquals(0, fe.attribs);
        }
    }

    @Test
    public void customAttributePreservation() throws Exception {
        ByteBuffer buf = createAttribCab();
        ParsedCab pc = parse(buf);
        assertEquals(CfFile.ATTRIB_READONLY | CfFile.ATTRIB_HIDDEN, pc.files[0].attribs);
    }

    @Test
    public void checksumFailure() throws Exception {
        ByteBuffer buf = createSampleCab();
        ParsedCab pc = parse(buf);

        ByteBuffer checksumBuf = ByteBuffer.allocate(pc.dataCbData[0] + 4);
        checksumBuf.order(ByteOrder.LITTLE_ENDIAN);
        checksumBuf.putShort(pc.dataCbData[0]);
        checksumBuf.putShort(pc.dataCbUncomp[0]);
        ByteBuffer dup = buf.duplicate();
        dup.order(ByteOrder.LITTLE_ENDIAN);
        dup.position(pc.folderCoffCabStart[0] + 8);
        ByteBuffer slice = dup.slice();
        slice.limit(pc.dataCbData[0]);
        checksumBuf.put(slice);
        checksumBuf.flip();
        int calculated = ChecksumHelper.cabChecksum(checksumBuf);
        assertEquals(pc.dataCsum[0], calculated);

        // corrupt one byte
        ByteBuffer corrupt = buf.duplicate();
        corrupt.put(pc.folderCoffCabStart[0] + 8, (byte) (corrupt.get(pc.folderCoffCabStart[0] + 8) ^ 0xFF));
        ParsedCab pc2 = parse(corrupt);
        ByteBuffer checksumBuf2 = ByteBuffer.allocate(pc2.dataCbData[0] + 4);
        checksumBuf2.order(ByteOrder.LITTLE_ENDIAN);
        checksumBuf2.putShort(pc2.dataCbData[0]);
        checksumBuf2.putShort(pc2.dataCbUncomp[0]);
        ByteBuffer dup2 = corrupt.duplicate();
        dup2.order(ByteOrder.LITTLE_ENDIAN);
        dup2.position(pc2.folderCoffCabStart[0] + 8);
        ByteBuffer slice2 = dup2.slice();
        slice2.limit(pc2.dataCbData[0]);
        checksumBuf2.put(slice2);
        checksumBuf2.flip();
        int calculated2 = ChecksumHelper.cabChecksum(checksumBuf2);
        assertNotEquals(pc2.dataCsum[0], calculated2);
    }

    @Test
    public void offsetRegression() throws Exception {
        ByteBuffer buf = createSampleCab();
        ParsedCab pc = parse(buf);
        assertEquals(0, pc.files[0].uoffFolderStart);
        assertEquals(pc.files[0].size, pc.files[1].uoffFolderStart);
    }
}
