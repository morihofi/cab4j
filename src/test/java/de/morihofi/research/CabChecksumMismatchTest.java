package de.morihofi.research;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class CabChecksumMismatchTest {

    @Test
    public void extractionFailsOnChecksumMismatch() throws Exception {
        ByteBuffer hello = ByteBuffer.wrap(Files.readAllBytes(Paths.get("test/hello.c")));

        CabFile cabFile = new CabFile();
        cabFile.addFile("hello.c", hello);

        ByteBuffer buf = cabFile.createCabinet();
        ByteBuffer corrupt = buf.duplicate();
        corrupt.order(ByteOrder.LITTLE_ENDIAN);

        // parse header to locate CFDATA data start
        corrupt.position(4 + 4 + 4 + 4); // signature + reserved1 + cbCabinet + reserved2
        int coffFiles = corrupt.getInt();
        corrupt.position(coffFiles);
        // skip file entry
        corrupt.getInt(); // cbFile
        corrupt.getInt(); // uoffFolderStart
        corrupt.getShort(); // iFolder
        corrupt.getShort(); // date
        corrupt.getShort(); // time
        corrupt.getShort(); // attribs
        while (corrupt.get() != 0) {
            // skip filename
        }
        // CFDATA header
        corrupt.getInt(); // csum
        corrupt.getShort(); // cbData
        corrupt.getShort(); // cbUncomp
        int dataStart = corrupt.position();

        // flip one byte inside the data block
        byte orig = corrupt.get(dataStart);
        corrupt.put(dataStart, (byte) (orig ^ 0xFF));
        corrupt.position(0);

        assertThrows(IllegalStateException.class, () -> CabExtractor.extract(corrupt));
    }
}
