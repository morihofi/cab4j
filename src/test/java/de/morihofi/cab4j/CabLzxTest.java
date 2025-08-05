package de.morihofi.cab4j;

import de.morihofi.cab4j.archive.CabArchive;
import de.morihofi.cab4j.generator.CabGenerator;
import de.morihofi.cab4j.structures.CfFolder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class CabLzxTest {
    @Test
    public void lzxCompressionRoundtrip() throws IOException {
        ByteBuffer hello = ByteBuffer.wrap(TestData.HELLO_C);
        CabArchive archive = new CabArchive();
        archive.addFile("hello.c", hello);
        CabGenerator generator = new CabGenerator(archive);
        generator.setCompressionType(CfFolder.COMPRESS_TYPE.TCOMP_TYPE_LZX);

        ByteBuffer cab = generator.createCabinet();
        Map<String, ByteBuffer> extracted = CabExtractor.extract(cab);

        assertArrayEquals(TestData.HELLO_C, TestData.toArray(extracted.get("hello.c")));
    }
}
