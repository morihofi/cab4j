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
        ByteBuffer buf = generator.createCabinet();
        Map<String, ByteBuffer> extracted = CabExtractor.extract(buf);
        byte[] orig = TestData.HELLO_C;
        byte[] out = new byte[extracted.get("hello.c").remaining()];
        extracted.get("hello.c").duplicate().get(out);
        assertArrayEquals(orig, out);
    }
}
