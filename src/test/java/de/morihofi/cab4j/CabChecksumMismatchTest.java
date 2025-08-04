package de.morihofi.cab4j;

import de.morihofi.cab4j.archive.CabArchive;
import de.morihofi.cab4j.generator.CabGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class CabChecksumMismatchTest {

    @Test
    public void extractionFailsOnChecksumMismatch() throws IOException {
        ByteBuffer hello = ByteBuffer.wrap(TestData.HELLO_C);

        CabArchive archive = new CabArchive();
        archive.addFile("hello.c", hello);
        CabGenerator generator = new CabGenerator(archive);

        ByteBuffer buf = generator.createCabinet();
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
